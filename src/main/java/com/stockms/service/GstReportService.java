package com.stockms.service;

import com.stockms.entity.*;
import com.stockms.repository.PurchaseRepository;
import com.stockms.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/** GST summary for a period: output tax on sales, input tax on purchases, and an HSN-wise summary. */
@Service
@Transactional(readOnly = true)
public class GstReportService {

    public record BillTax(Long id, String billNumber, LocalDate date, String party, String partyGstin,
                          BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal total) {
        public BigDecimal tax() {
            return cgst.add(sgst).add(igst);
        }

        static BigDecimal sum(List<BillTax> rows, java.util.function.Function<BillTax, BigDecimal> field) {
            return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        static BillTax total(List<BillTax> rows) {
            return new BillTax(null, "Total", null, null, null,
                    sum(rows, BillTax::taxable), sum(rows, BillTax::cgst), sum(rows, BillTax::sgst),
                    sum(rows, BillTax::igst), sum(rows, BillTax::total));
        }
    }

    public record HsnRow(String hsn, BigDecimal rate, int quantity, BigDecimal taxable, BigDecimal tax) {
    }

    public record Report(LocalDate from, LocalDate to,
                         List<BillTax> sales, BillTax salesTotal,
                         List<BillTax> purchases, BillTax purchasesTotal,
                         List<HsnRow> hsnSummary) {
        /** Output GST minus input tax credit; negative means credit carried forward. */
        public BigDecimal netPayable() {
            return salesTotal.tax().subtract(purchasesTotal.tax());
        }
    }

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;

    public GstReportService(SaleRepository saleRepository, PurchaseRepository purchaseRepository) {
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
    }

    public Report report(LocalDate from, LocalDate to) {
        List<Sale> sales = saleRepository.findByDateBetweenOrderByDateAscIdAsc(from, to);
        List<Purchase> purchases = purchaseRepository.findByDateBetweenOrderByDateAscIdAsc(from, to);

        List<BillTax> saleRows = sales.stream().map(s -> new BillTax(s.getId(), s.getBillNumber(), s.getDate(),
                s.getPartyName(), s.getCustomer().getGstin(), s.getTaxableAmount(), s.getCgstAmount(),
                s.getSgstAmount(), s.getIgstAmount(), s.getSubtotal())).toList();
        List<BillTax> purchaseRows = purchases.stream().map(p -> new BillTax(p.getId(), p.getBillNumber(), p.getDate(),
                p.getPartyName(), p.getSupplier().getGstin(), p.getTaxableAmount(), p.getCgstAmount(),
                p.getSgstAmount(), p.getIgstAmount(), p.getSubtotal())).toList();

        return new Report(from, to, saleRows, BillTax.total(saleRows), purchaseRows, BillTax.total(purchaseRows),
                hsnSummary(sales.stream().flatMap(s -> s.getItems().stream())));
    }

    /** Sales grouped by HSN code and rate, as needed for GSTR-1. */
    private static List<HsnRow> hsnSummary(Stream<SaleItem> items) {
        Map<String, HsnRow> rows = new TreeMap<>();
        items.forEach(item -> {
            String hsn = item.getHsnCode() == null ? "" : item.getHsnCode();
            String key = hsn + "|" + item.getGstRate().stripTrailingZeros().toPlainString();
            rows.merge(key, new HsnRow(hsn, item.getGstRate(), item.getQuantity(), item.getTaxableValue(), item.getTaxAmount()),
                    (a, b) -> new HsnRow(a.hsn(), a.rate(), a.quantity() + b.quantity(),
                            a.taxable().add(b.taxable()), a.tax().add(b.tax())));
        });
        return new ArrayList<>(rows.values());
    }
}
