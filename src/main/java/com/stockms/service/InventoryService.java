package com.stockms.service;

import com.stockms.entity.*;
import com.stockms.repository.*;
import com.stockms.service.DocNumberService.DocType;
import com.stockms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records purchases, sales and payments in the current branch, keeping that branch's stock
 * and the supplier/customer balances consistent with them.
 */
@Service
@Transactional
public class InventoryService {

    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final PurchaseRepository purchaseRepository;
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final BranchRepository branchRepository;
    private final CompanyService companyService;
    private final StockService stockService;
    private final DocNumberService docNumberService;

    public InventoryService(ProductRepository productRepository,
                            SupplierRepository supplierRepository,
                            CustomerRepository customerRepository,
                            PurchaseRepository purchaseRepository,
                            SaleRepository saleRepository,
                            PaymentRepository paymentRepository,
                            BranchRepository branchRepository,
                            CompanyService companyService,
                            StockService stockService,
                            DocNumberService docNumberService) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.branchRepository = branchRepository;
        this.companyService = companyService;
        this.stockService = stockService;
        this.docNumberService = docNumberService;
    }

    /** Running GST totals for one bill, split into CGST/SGST or IGST. */
    private static final class TaxTotals {
        final boolean interState;
        BigDecimal taxable = BigDecimal.ZERO;
        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        TaxTotals(boolean interState) {
            this.interState = interState;
        }

        void add(BigDecimal taxableValue, BigDecimal tax) {
            taxable = taxable.add(taxableValue);
            if (interState) {
                igst = igst.add(tax);
            } else {
                BigDecimal central = Gst.cgstPart(tax);
                cgst = cgst.add(central);
                sgst = sgst.add(tax.subtract(central));
            }
            total = total.add(taxableValue).add(tax);
        }
    }

    public Purchase createPurchase(TransactionForm form, String username) {
        if (form.getPartyId() == null) throw new BusinessException("Please select a supplier.");
        Supplier supplier = supplierRepository.findById(form.getPartyId())
                .orElseThrow(() -> new BusinessException("Selected supplier no longer exists."));

        Branch branch = currentBranch();
        Purchase purchase = new Purchase();
        purchase.setBranch(branch);
        purchase.setSupplier(supplier);
        fillHeader(form, username, purchase::setDate, purchase::setMode, purchase::setDescription, purchase::setCreatedBy);

        TaxTotals totals = new TaxTotals(Gst.isInterState(branch.getState(), supplier.getState()));
        for (TransactionForm.Line line : checkedLines(form)) {
            Product product = findProduct(line.getProductId());
            PurchaseItem item = new PurchaseItem(product, line.getQuantity(), line.getPrice());
            purchase.addItem(item);
            stockService.add(branch, product, line.getQuantity());
            product.setCostPrice(line.getPrice());
            totals.add(item.getTaxableValue(), item.getTaxAmount());
        }

        BigDecimal paid = checkedPaid(form.getPaid(), totals.total);
        purchase.setInterState(totals.interState);
        purchase.setTaxableAmount(totals.taxable);
        purchase.setCgstAmount(totals.cgst);
        purchase.setSgstAmount(totals.sgst);
        purchase.setIgstAmount(totals.igst);
        purchase.setSubtotal(totals.total);
        purchase.setPaid(paid);
        purchase.setDue(totals.total.subtract(paid));
        supplier.setBalance(supplier.getBalance().add(purchase.getDue()));

        purchase.setBillNumber(docNumberService.next(companyService.current(), branch, DocType.PURCHASE, purchase.getDate()));
        return purchaseRepository.save(purchase);
    }

    public Sale createSale(TransactionForm form, String username) {
        if (form.getPartyId() == null) throw new BusinessException("Please select a customer.");
        Customer customer = customerRepository.findById(form.getPartyId())
                .orElseThrow(() -> new BusinessException("Selected customer no longer exists."));

        Branch branch = currentBranch();
        Sale sale = new Sale();
        sale.setBranch(branch);
        sale.setCustomer(customer);
        fillHeader(form, username, sale::setDate, sale::setMode, sale::setDescription, sale::setCreatedBy);

        // GST depends on where the selling branch is registered, not the company's head office.
        TaxTotals totals = new TaxTotals(Gst.isInterState(branch.getState(), customer.getState()));
        for (TransactionForm.Line line : checkedLines(form)) {
            Product product = findProduct(line.getProductId());
            SaleItem item = new SaleItem(product, line.getQuantity(), line.getPrice());
            sale.addItem(item);
            stockService.remove(branch, product, line.getQuantity());
            totals.add(item.getTaxableValue(), item.getTaxAmount());
        }

        BigDecimal paid = checkedPaid(form.getPaid(), totals.total);
        sale.setInterState(totals.interState);
        sale.setTaxableAmount(totals.taxable);
        sale.setCgstAmount(totals.cgst);
        sale.setSgstAmount(totals.sgst);
        sale.setIgstAmount(totals.igst);
        sale.setSubtotal(totals.total);
        sale.setPaid(paid);
        sale.setDue(totals.total.subtract(paid));
        customer.setBalance(customer.getBalance().add(sale.getDue()));

        sale.setBillNumber(docNumberService.next(companyService.current(), branch, DocType.SALE, sale.getDate()));
        return saleRepository.save(sale);
    }

    /** Deletes a purchase, taking its goods back out of stock and reversing the supplier due. */
    public void deletePurchase(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Purchase not found."));
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            if (!stockService.tryRemove(purchase.getBranch(), product, item.getQuantity())) {
                throw new BusinessException("Cannot delete: %s has already been sold (only %d left in %s)."
                        .formatted(product.getName(), stockService.quantity(purchase.getBranch(), product),
                                purchase.getBranch().getName()));
            }
        }
        Supplier supplier = purchase.getSupplier();
        supplier.setBalance(supplier.getBalance().subtract(purchase.getDue()));
        purchaseRepository.delete(purchase);
    }

    /** Deletes a sale, returning its goods to stock and reversing the customer due. */
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Sale not found."));
        for (SaleItem item : sale.getItems()) {
            stockService.add(sale.getBranch(), item.getProduct(), item.getQuantity());
        }
        Customer customer = sale.getCustomer();
        customer.setBalance(customer.getBalance().subtract(sale.getDue()));
        saleRepository.delete(sale);
    }

    public Payment recordPayment(Payment payment, String username) {
        // The party may come from a screen and be stale; reload it so the balance
        // check and update use the current row (the other app may have changed it).
        if (payment.getType() == PaymentType.SUPPLIER_PAYMENT) {
            if (payment.getSupplier() == null) throw new BusinessException("Please select a supplier.");
            Supplier supplier = supplierRepository.findById(payment.getSupplier().getId())
                    .orElseThrow(() -> new BusinessException("Selected supplier no longer exists."));
            checkNotOverpaid(payment.getAmount(), supplier.getBalance());
            payment.setSupplier(supplier);
            payment.setCustomer(null);
            supplier.setBalance(supplier.getBalance().subtract(payment.getAmount()));
        } else {
            if (payment.getCustomer() == null) throw new BusinessException("Please select a customer.");
            Customer customer = customerRepository.findById(payment.getCustomer().getId())
                    .orElseThrow(() -> new BusinessException("Selected customer no longer exists."));
            checkNotOverpaid(payment.getAmount(), customer.getBalance());
            payment.setCustomer(customer);
            payment.setSupplier(null);
            customer.setBalance(customer.getBalance().subtract(payment.getAmount()));
        }
        payment.setBranch(currentBranch());
        payment.setCreatedBy(username);
        return paymentRepository.save(payment);
    }

    private void fillHeader(TransactionForm form, String username,
                            Consumer<LocalDate> date,
                            Consumer<String> mode,
                            Consumer<String> description,
                            Consumer<String> createdBy) {
        date.accept(form.getDate() != null ? form.getDate() : LocalDate.now());
        mode.accept(form.getMode());
        description.accept(form.getDescription());
        createdBy.accept(username);
    }

    private List<TransactionForm.Line> checkedLines(TransactionForm form) {
        List<TransactionForm.Line> lines = form.filledLines();
        if (lines.isEmpty()) throw new BusinessException("Add at least one item.");
        int row = 1;
        for (TransactionForm.Line line : lines) {
            if (line.getProductId() == null) throw new BusinessException("Row %d: select a product.".formatted(row));
            if (line.getQuantity() == null || line.getQuantity() <= 0)
                throw new BusinessException("Row %d: quantity must be at least 1.".formatted(row));
            if (line.getPrice() == null || line.getPrice().signum() < 0)
                throw new BusinessException("Row %d: enter a valid price.".formatted(row));
            row++;
        }
        return lines;
    }

    private BigDecimal checkedPaid(BigDecimal paid, BigDecimal subtotal) {
        BigDecimal amount = paid == null ? BigDecimal.ZERO : paid;
        if (amount.signum() < 0) throw new BusinessException("Paid amount cannot be negative.");
        if (amount.compareTo(subtotal) > 0) throw new BusinessException("Paid amount cannot exceed the bill total.");
        return amount;
    }

    private void checkNotOverpaid(BigDecimal amount, BigDecimal balance) {
        if (amount.compareTo(balance) > 0) {
            throw new BusinessException("Amount exceeds the outstanding balance of " + balance + ".");
        }
    }

    /** The branch selected in the top bar; new bills and payments are recorded there. */
    private Branch currentBranch() {
        Branch branch = branchRepository.findById(TenantContext.require().requireBranchId())
                .orElseThrow(() -> new BusinessException("Selected branch no longer exists."));
        if (!branch.isActive()) throw new BusinessException("Branch " + branch.getName() + " is inactive.");
        return branch;
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Selected product no longer exists."));
    }
}
