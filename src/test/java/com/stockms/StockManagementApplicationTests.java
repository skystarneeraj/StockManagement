package com.stockms;

import com.stockms.entity.*;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.service.BusinessException;
import com.stockms.service.Gst;
import com.stockms.service.SettingsService;
import com.stockms.service.InventoryService;
import com.stockms.service.TransactionForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:stocktest;DB_CLOSE_DELAY=-1")
@Transactional
class StockManagementApplicationTests {

    @Autowired InventoryService inventoryService;
    @Autowired ProductRepository productRepository;
    @Autowired SupplierRepository supplierRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SettingsService settingsService;

    @Test
    void purchaseAddsStockAndSupplierDue() {
        Product mouse = productRepository.findByCodeIgnoreCase("P001").orElseThrow();
        Supplier supplier = supplierRepository.findAll().getFirst();
        int stockBefore = mouse.getQuantity();
        BigDecimal balanceBefore = supplier.getBalance();

        Purchase purchase = inventoryService.createPurchase(
                form(supplier.getId(), "1000", line(mouse, 10, "240")), "admin");

        // 10 x 240 = 2400 taxable + 18% GST (432) = 2832; 1000 paid leaves 1832 due
        assertThat(purchase.getBillNumber()).startsWith("PUR-");
        assertThat(purchase.getTaxableAmount()).isEqualByComparingTo("2400");
        assertThat(purchase.getTaxAmount()).isEqualByComparingTo("432");
        assertThat(purchase.getSubtotal()).isEqualByComparingTo("2832");
        assertThat(mouse.getQuantity()).isEqualTo(stockBefore + 10);
        assertThat(mouse.getCostPrice()).isEqualByComparingTo("240");
        assertThat(supplier.getBalance()).isEqualByComparingTo(balanceBefore.add(new BigDecimal("1832")));
    }

    @Test
    void saleRemovesStockAndCanBeReversed() {
        Product keyboard = productRepository.findByCodeIgnoreCase("P002").orElseThrow();
        Customer customer = customerRepository.findAll().getFirst();
        int stockBefore = keyboard.getQuantity();
        BigDecimal balanceBefore = customer.getBalance();

        Sale sale = inventoryService.createSale(form(customer.getId(), "500", line(keyboard, 2, "600")), "admin");

        // 2 x 600 = 1200 + 18% GST (216) = 1416; 500 paid leaves 916 due
        assertThat(sale.getBillNumber()).startsWith("INV-");
        assertThat(sale.getDue()).isEqualByComparingTo("916");
        assertThat(keyboard.getQuantity()).isEqualTo(stockBefore - 2);
        assertThat(customer.getBalance()).isEqualByComparingTo(balanceBefore.add(new BigDecimal("916")));

        inventoryService.deleteSale(sale.getId());

        assertThat(keyboard.getQuantity()).isEqualTo(stockBefore);
        assertThat(customer.getBalance()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void cannotSellMoreThanInStock() {
        Product paper = productRepository.findByCodeIgnoreCase("P004").orElseThrow();
        Customer customer = customerRepository.findAll().getFirst();

        assertThatThrownBy(() -> inventoryService.createSale(
                form(customer.getId(), "0", line(paper, paper.getQuantity() + 1, "280")), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough stock");
    }

    @Test
    void cannotPayMoreThanBillTotal() {
        Product mouse = productRepository.findByCodeIgnoreCase("P001").orElseThrow();
        Customer customer = customerRepository.findAll().getFirst();

        assertThatThrownBy(() -> inventoryService.createSale(form(customer.getId(), "999", line(mouse, 1, "350")), "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");
    }

    @Test
    void customerPaymentReducesBalance() {
        Product mouse = productRepository.findByCodeIgnoreCase("P001").orElseThrow();
        Customer customer = customerRepository.findAll().getFirst();
        inventoryService.createSale(form(customer.getId(), "0", line(mouse, 2, "350")), "admin");
        BigDecimal owed = customer.getBalance();

        Payment payment = new Payment();
        payment.setType(PaymentType.CUSTOMER_RECEIPT);
        payment.setCustomer(customer);
        payment.setAmount(new BigDecimal("300"));
        inventoryService.recordPayment(payment, "admin");

        assertThat(customer.getBalance()).isEqualByComparingTo(owed.subtract(new BigDecimal("300")));

        Payment tooMuch = new Payment();
        tooMuch.setType(PaymentType.CUSTOMER_RECEIPT);
        tooMuch.setCustomer(customer);
        tooMuch.setAmount(customer.getBalance().add(BigDecimal.ONE));
        assertThatThrownBy(() -> inventoryService.recordPayment(tooMuch, "admin"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void sameStateSaleSplitsGstIntoCgstAndSgst() {
        Product rice = productRepository.findByCodeIgnoreCase("P003").orElseThrow(); // 5% GST
        Customer customer = customerRepository.findAll().getFirst();
        settingsService.save(company("Tamil Nadu"));
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
        Product mouse = productRepository.findByCodeIgnoreCase("P001").orElseThrow(); // 18% GST
        Customer customer = customerRepository.findAll().getFirst();
        settingsService.save(company("Tamil Nadu"));
        customer.setState("Karnataka");

        Sale sale = inventoryService.createSale(form(customer.getId(), "0", line(mouse, 1, "350")), "admin");

        assertThat(sale.isInterState()).isTrue();
        assertThat(sale.getIgstAmount()).isEqualByComparingTo("63");
        assertThat(sale.getCgstAmount().add(sale.getSgstAmount())).isEqualByComparingTo("0");
        assertThat(sale.getSubtotal()).isEqualByComparingTo("413");
    }

    @Test
    void gstinGivesState() {
        assertThat(Gst.stateFromGstin("33ABCDE1234F1Z5")).isEqualTo("Tamil Nadu");
        assertThat(Gst.stateFromGstin("not-a-gstin")).isNull();
    }

    private static CompanySettings company(String state) {
        CompanySettings company = new CompanySettings();
        company.setName("Test Traders");
        company.setState(state);
        return company;
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
