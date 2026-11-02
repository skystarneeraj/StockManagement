package com.stockms.web;

import com.stockms.entity.Product;
import com.stockms.entity.Purchase;
import com.stockms.entity.Sale;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.PurchaseRepository;
import com.stockms.repository.SaleRepository;
import com.stockms.service.GstReportService;
import com.stockms.service.SettingsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final GstReportService gstReportService;
    private final SettingsService settingsService;

    public ReportController(SaleRepository saleRepository, PurchaseRepository purchaseRepository,
                            ProductRepository productRepository, GstReportService gstReportService,
                            SettingsService settingsService) {
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.gstReportService = gstReportService;
        this.settingsService = settingsService;
    }

    @GetMapping("/sales")
    String sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                 Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        List<Sale> rows = saleRepository.findByDateBetweenOrderByDateAscIdAsc(start, end);
        model.addAttribute("rows", rows);
        model.addAttribute("total", sum(rows, Sale::getSubtotal));
        model.addAttribute("paid", sum(rows, Sale::getPaid));
        model.addAttribute("due", sum(rows, Sale::getDue));
        model.addAttribute("isSale", true);
        return dateRange(model, start, end, "Sales Report", "sales");
    }

    @GetMapping("/purchases")
    String purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        List<Purchase> rows = purchaseRepository.findByDateBetweenOrderByDateAscIdAsc(start, end);
        model.addAttribute("rows", rows);
        model.addAttribute("total", sum(rows, Purchase::getSubtotal));
        model.addAttribute("paid", sum(rows, Purchase::getPaid));
        model.addAttribute("due", sum(rows, Purchase::getDue));
        model.addAttribute("isSale", false);
        return dateRange(model, start, end, "Purchase Report", "purchases");
    }

    @GetMapping("/gst")
    String gst(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
               Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("report", gstReportService.report(start, end));
        model.addAttribute("company", settingsService.get());
        return "reports/gst";
    }

    @GetMapping("/stock")
    String stock(Model model) {
        List<Product> products = productRepository.findAllByOrderByNameAsc();
        model.addAttribute("products", products);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("totalValue", sum(products, Product::getStockValue));
        model.addAttribute("totalUnits", products.stream().mapToInt(Product::getQuantity).sum());
        model.addAttribute("lowCount", products.stream().filter(Product::isLowStock).count());
        return "reports/stock";
    }

    private String dateRange(Model model, LocalDate from, LocalDate to, String title, String section) {
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("title", title);
        model.addAttribute("section", section);
        return "reports/transactions";
    }

    private static <T> BigDecimal sum(List<T> rows, Function<T, BigDecimal> field) {
        return rows.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
