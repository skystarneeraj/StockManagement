package com.stockms.service;

import com.stockms.entity.*;
import com.stockms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * Records purchases, sales and payments, keeping product quantities and
 * supplier/customer balances consistent with them.
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
    private final SettingsService settingsService;

    public InventoryService(ProductRepository productRepository,
                            SupplierRepository supplierRepository,
                            CustomerRepository customerRepository,
                            PurchaseRepository purchaseRepository,
                            SaleRepository saleRepository,
                            PaymentRepository paymentRepository,
                            SettingsService settingsService) {
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.purchaseRepository = purchaseRepository;
        this.saleRepository = saleRepository;
        this.paymentRepository = paymentRepository;
        this.settingsService = settingsService;
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

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        fillHeader(form, username, purchase::setDate, purchase::setMode, purchase::setDescription, purchase::setCreatedBy);

        TaxTotals totals = new TaxTotals(Gst.isInterState(settingsService.get().getState(), supplier.getState()));
        for (TransactionForm.Line line : checkedLines(form)) {
            Product product = findProduct(line.getProductId());
            PurchaseItem item = new PurchaseItem(product, line.getQuantity(), line.getPrice());
            purchase.addItem(item);
            product.setQuantity(product.getQuantity() + line.getQuantity());
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

        purchaseRepository.save(purchase);
        purchase.setBillNumber("PUR-%05d".formatted(purchase.getId()));
        return purchase;
    }

    public Sale createSale(TransactionForm form, String username) {
        if (form.getPartyId() == null) throw new BusinessException("Please select a customer.");
        Customer customer = customerRepository.findById(form.getPartyId())
                .orElseThrow(() -> new BusinessException("Selected customer no longer exists."));

        Sale sale = new Sale();
        sale.setCustomer(customer);
        fillHeader(form, username, sale::setDate, sale::setMode, sale::setDescription, sale::setCreatedBy);

        TaxTotals totals = new TaxTotals(Gst.isInterState(settingsService.get().getState(), customer.getState()));
        for (TransactionForm.Line line : checkedLines(form)) {
            Product product = findProduct(line.getProductId());
            if (product.getQuantity() < line.getQuantity()) {
                throw new BusinessException("Not enough stock for %s: %d available, %d requested."
                        .formatted(product.getName(), product.getQuantity(), line.getQuantity()));
            }
            SaleItem item = new SaleItem(product, line.getQuantity(), line.getPrice());
            sale.addItem(item);
            product.setQuantity(product.getQuantity() - line.getQuantity());
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

        saleRepository.save(sale);
        sale.setBillNumber("INV-%05d".formatted(sale.getId()));
        return sale;
    }

    /** Deletes a purchase, taking its goods back out of stock and reversing the supplier due. */
    public void deletePurchase(Long id) {
        Purchase purchase = purchaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Purchase not found."));
        for (PurchaseItem item : purchase.getItems()) {
            Product product = item.getProduct();
            if (product.getQuantity() < item.getQuantity()) {
                throw new BusinessException("Cannot delete: %s has already been sold (only %d left in stock)."
                        .formatted(product.getName(), product.getQuantity()));
            }
            product.setQuantity(product.getQuantity() - item.getQuantity());
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
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity() + item.getQuantity());
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

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Selected product no longer exists."));
    }
}
