package com.stockms;

import com.stockms.entity.*;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.CompanyRepository;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.service.*;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TestTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Business rules, run as the sample company's admin; each test is rolled back. */
@StockMsTest
@Transactional
class StockManagementApplicationTests {

    @Autowired InventoryService inventoryService;
    @Autowired StockService stockService;
    @Autowired StockTransferService transferService;
    @Autowired ProductRepository productRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired BranchRepository branchRepository;

    Company company;
    Branch headOffice;
    Branch delhi;

    @BeforeEach
    void signInAsAdmin() {
        company = companyRepository.findByCodeIgnoreCase("MC").orElseThrow();
        TestTenants.set(Tenant.system(company.getId(), Set.of(), null));
        headOffice = branchRepository.findAllByOrderByHeadOfficeDescNameAsc().getFirst();
        delhi = new Branch();
        delhi.setName("Delhi");
        delhi.setCode("DEL");
        delhi.setState("Delhi");
        branchRepository.save(delhi);
        workIn(headOffice);
    }

    @AfterEach
    void signOut() {
        TestTenants.clear();
    }

    private void workIn(Branch branch) {
        TestTenants.set(TestTenants.admin(company, branch, headOffice, delhi));
    }

    @Test
    void purchaseAddsStockToTheBranchAndSupplierDue() {
        Product mouse = product("P001");
        Supplier supplier = supplierRepository.findAll().getFirst();
        int stockBefore = stockService.quantity(headOffice, mouse);
        BigDecimal balanceBefore = supplier.getBalance();

        Purchase purchase = inventoryService.createPurchase(
                form(supplier.getId(), "1000", line(mouse, 10, "240")), "admin");

        // 10 x 240 = 2400 taxable + 18% GST (432) = 2832; 1000 paid leaves 1832 due
        assertThat(purchase.getBillNumber()).matches("MC-HO-PUR-\\d{4}-\\d{2}/\\d{4}");
        assertThat(purchase.getBranch().getId()).isEqualTo(headOffice.getId());
        assertThat(purchase.getTaxableAmount()).isEqualByComparingTo("2400");
        assertThat(purchase.getTaxAmount()).isEqualByComparingTo("432");
        assertThat(purchase.getSubtotal()).isEqualByComparingTo("2832");
        assertThat(stockService.quantity(headOffice, mouse)).isEqualTo(stockBefore + 10);
        assertThat(stockService.quantity(delhi, mouse)).isZero();
        assertThat(mouse.getCostPrice()).isEqualByComparingTo("240");
        assertThat(supplier.getBalance()).isEqualByComparingTo(balanceBefore.add(new BigDecimal("1832")));
    }

    @Test
    void saleRemovesBranchStockAndCanBeReversed() {
        Product keyboard = product("P002");
        Customer customer = customerRepository.findAll().getFirst();
        int stockBefore = stockService.quantity(headOffice, keyboard);
        BigDecimal balanceBefore = customer.getBalance();

        Sale sale = inventoryService.createSale(form(customer.getId(), "500", line(keyboard, 2, "600")), "admin");

        // 2 x 600 = 1200 + 18% GST (216) = 1416; 500 paid leaves 916 due
        assertThat(sale.getBillNumber()).matches("MC-HO-\\d{4}-\\d{2}/\\d{4}");
        assertThat(sale.getDue()).isEqualByComparingTo("916");
        assertThat(stockService.quantity(headOffice, keyboard)).isEqualTo(stockBefore - 2);
        assertThat(customer.getBalance()).isEqualByComparingTo(balanceBefore.add(new BigDecimal("916")));

        inventoryService.deleteSale(sale.getId());

        assertThat(stockService.quantity(headOffice, keyboard)).isEqualTo(stockBefore);
        assertThat(customer.getBalance()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void cannotSellMoreThanTheBranchHas() {
        Product paper = product("P004");
        Customer customer = customerRepository.findAll().getFirst();
        int inHeadOffice = stockService.quantity(headOffice, paper);

        assertThatThrownBy(() -> inventoryService.createSale(
                form(customer.getId(), "0", line(paper, inHeadOffice + 1, "280")), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");

        // Delhi has none at all, even though the company does.
        workIn(delhi);
        assertThatThrownBy(() -> inventoryService.createSale(form(customer.getId(), "0", line(paper, 1, "280")), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock for A4 Paper Ream in Delhi: 0 available");
    }

    @Test
    void negativeStockIsAllowedOnlyWhenTheCompanyAllowsIt() {
        Product paper = product("P004");
        Customer customer = customerRepository.findAll().getFirst();
        int inStock = stockService.quantity(headOffice, paper);
        company.setAllowNegativeStock(true);

        inventoryService.createSale(form(customer.getId(), "0", line(paper, inStock + 5, "280")), "admin");

        assertThat(stockService.quantity(headOffice, paper)).isEqualTo(-5);
    }

    @Test
    void cannotPayMoreThanBillTotal() {
        Product mouse = product("P001");
        Customer customer = customerRepository.findAll().getFirst();

        assertThatThrownBy(() -> inventoryService.createSale(form(customer.getId(), "999", line(mouse, 1, "350")), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
    }

    @Test
    void customerPaymentReducesBalanceAndIsRecordedInTheBranch() {
        Product mouse = product("P001");
        Customer customer = customerRepository.findAll().getFirst();
        inventoryService.createSale(form(customer.getId(), "0", line(mouse, 2, "350")), "admin");
        BigDecimal owed = customer.getBalance();

        Payment payment = new Payment();
        payment.setType(PaymentType.CUSTOMER_RECEIPT);
        payment.setCustomer(customer);
        payment.setAmount(new BigDecimal("300"));
        inventoryService.recordPayment(payment, "admin");

        assertThat(customer.getBalance()).isEqualByComparingTo(owed.subtract(new BigDecimal("300")));
        assertThat(payment.getBranch().getId()).isEqualTo(headOffice.getId());

        Payment tooMuch = new Payment();
        tooMuch.setType(PaymentType.CUSTOMER_RECEIPT);
        tooMuch.setCustomer(customer);
        tooMuch.setAmount(customer.getBalance().add(BigDecimal.ONE));
        assertThatThrownBy(() -> inventoryService.recordPayment(tooMuch, "admin"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sameStateSaleSplitsGstIntoCgstAndSgst() {
        Product rice = product("P003"); // 5% GST
        Customer customer = customerRepository.findAll().getFirst();
        headOffice.setState("Tamil Nadu");
        customer.setState("Tamil Nadu");

        Sale sale = inventoryService.createSale(form(customer.getId(), "0", line(rice, 3, "95.30")), "admin");

        // 3 x 95.30 = 285.90; 5% = 14.295 -> 14.30, split 7.15 + 7.15
        assertThat(sale.isInterState()).isFalse();
        assertThat(sale.getTaxableAmount()).isEqualByComparingTo("285.90");
        assertThat(sale.getCgstAmount()).isEqualByComparingTo("7.15");
        assertThat(sale.getSgstAmount()).isEqualByComparingTo("7.15");
        assertThat(sale.getIgstAmount()).isEqualByComparingTo("0");
        assertThat(sale.getSubtotal()).isEqualByComparingTo("300.20");
        assertThat(sale.getItems().getFirst().getHsnCode()).isEqualTo("1006");
    }

    @Test
    void otherStateSaleChargesIgst() {
        Product mouse = product("P001"); // 18% GST
        Customer customer = customerRepository.findAll().getFirst();
        headOffice.setState("Tamil Nadu");
        customer.setState("Karnataka");

        Sale sale = inventoryService.createSale(form(customer.getId(), "0", line(mouse, 1, "350")), "admin");

        assertThat(sale.isInterState()).isTrue();
        assertThat(sale.getIgstAmount()).isEqualByComparingTo("63");
        assertThat(sale.getCgstAmount().add(sale.getSgstAmount())).isEqualByComparingTo("0");
        assertThat(sale.getSubtotal()).isEqualByComparingTo("413");
    }

    @Test
    void gstComparesTheCustomerWithTheSellingBranchNotTheCompany() {
        Product mouse = product("P001");
        Customer customer = customerRepository.findAll().getFirst();
        company.setState("Delhi");
        headOffice.setState("Tamil Nadu");
        customer.setState("Delhi");
        stockService.add(delhi, mouse, 5);

        Sale fromHeadOffice = inventoryService.createSale(form(customer.getId(), "0", line(mouse, 1, "350")), "admin");
        workIn(delhi);
        Sale fromDelhi = inventoryService.createSale(form(customer.getId(), "0", line(mouse, 1, "350")), "admin");

        assertThat(fromHeadOffice.isInterState()).isTrue();   // Tamil Nadu branch -> Delhi customer
        assertThat(fromDelhi.isInterState()).isFalse();       // Delhi branch -> Delhi customer
    }

    @Test
    void invoiceNumbersArePerBranchAndFinancialYear() {
        Product mouse = product("P001");
        Customer customer = customerRepository.findAll().getFirst();
        stockService.add(delhi, mouse, 5);

        TransactionForm march = form(customer.getId(), "0", line(mouse, 1, "350"));
        march.setDate(LocalDate.of(2031, 3, 31));
        TransactionForm april = form(customer.getId(), "0", line(mouse, 1, "350"));
        april.setDate(LocalDate.of(2031, 4, 1));
        TransactionForm april2 = form(customer.getId(), "0", line(mouse, 1, "350"));
        april2.setDate(LocalDate.of(2031, 4, 2));

        assertThat(inventoryService.createSale(march, "admin").getBillNumber()).isEqualTo("MC-HO-2030-31/0001");
        assertThat(inventoryService.createSale(april, "admin").getBillNumber()).isEqualTo("MC-HO-2031-32/0001");
        assertThat(inventoryService.createSale(april2, "admin").getBillNumber()).isEqualTo("MC-HO-2031-32/0002");
        workIn(delhi);
        assertThat(inventoryService.createSale(april, "admin").getBillNumber()).isEqualTo("MC-DEL-2031-32/0001");
    }

    @Test
    void fiscalYearRunsFromAprilToMarch() {
        assertThat(DocNumberService.fiscalYear(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
        assertThat(DocNumberService.fiscalYear(LocalDate.of(2027, 3, 31))).isEqualTo("2026-27");
        assertThat(DocNumberService.fiscalYear(LocalDate.of(2099, 12, 31))).isEqualTo("2099-00");
    }

    @Test
    void transferMovesStockOnDispatchAndReceipt() {
        Product mouse = product("P001");
        int headOfficeBefore = stockService.quantity(headOffice, mouse);

        StockTransfer transfer = transferService.create(transferForm(delhi, mouse, 5), "admin");
        assertThat(transfer.getStatus()).isEqualTo(StockTransfer.Status.DRAFT);
        assertThat(transfer.getTransferNumber()).matches("MC-HO-TRF-\\d{4}-\\d{2}/\\d{4}");
        assertThat(stockService.quantity(headOffice, mouse)).isEqualTo(headOfficeBefore);

        transferService.dispatch(transfer.getId());
        assertThat(transfer.getStatus()).isEqualTo(StockTransfer.Status.IN_TRANSIT);
        assertThat(stockService.quantity(headOffice, mouse)).isEqualTo(headOfficeBefore - 5);
        assertThat(stockService.quantity(delhi, mouse)).isZero();

        transferService.receive(transfer.getId(), "admin");
        assertThat(transfer.getStatus()).isEqualTo(StockTransfer.Status.RECEIVED);
        assertThat(stockService.quantity(headOffice, mouse)).isEqualTo(headOfficeBefore - 5);
        assertThat(stockService.quantity(delhi, mouse)).isEqualTo(5);
    }

    @Test
    void recalledTransferReturnsStockAndCannotSendMoreThanInStock() {
        Product paper = product("P004");
        int inStock = stockService.quantity(headOffice, paper);

        StockTransfer tooMuch = transferService.create(transferForm(delhi, paper, inStock + 1), "admin");
        assertThatThrownBy(() -> transferService.dispatch(tooMuch.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Not enough stock");

        StockTransfer transfer = transferService.create(transferForm(delhi, paper, 1), "admin");
        transferService.dispatch(transfer.getId());
        transferService.recall(transfer.getId());

        assertThat(transfer.getStatus()).isEqualTo(StockTransfer.Status.DRAFT);
        assertThat(stockService.quantity(headOffice, paper)).isEqualTo(inStock);
        assertThatThrownBy(() -> transferService.receive(transfer.getId(), "admin"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("in transit");
    }

    @Test
    void gstinGivesState() {
        assertThat(Gst.stateFromGstin("33ABCDE1234F1Z5")).isEqualTo("Tamil Nadu");
        assertThat(Gst.stateFromGstin("not-a-gstin")).isNull();
    }

    private Product product(String code) {
        return productRepository.findByCodeIgnoreCase(code).orElseThrow();
    }

    private static TransferForm transferForm(Branch to, Product product, int quantity) {
        TransferForm form = new TransferForm();
        form.setToBranchId(to.getId());
        form.getLines().add(new TransferForm.Line(product.getId(), quantity));
        form.getLines().add(new TransferForm.Line()); // an untouched row in the form is ignored
        return form;
    }

    private static TransactionForm form(Long partyId, String paid, TransactionForm.Line... lines) {
        TransactionForm form = new TransactionForm();
        form.setPartyId(partyId);
        form.setPaid(new BigDecimal(paid));
        form.getLines().addAll(java.util.List.of(lines));
        form.getLines().add(new TransactionForm.Line()); // an untouched row in the form is ignored
        return form;
    }

    private static TransactionForm.Line line(Product product, int quantity, String price) {
        return new TransactionForm.Line(product.getId(), quantity, new BigDecimal(price));
    }
}
