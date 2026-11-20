package com.stockms.web;

import com.stockms.entity.Branch;
import com.stockms.entity.Product;
import com.stockms.entity.Purchase;
import com.stockms.entity.Sale;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.PurchaseRepository;
import com.stockms.repository.SaleRepository;
import com.stockms.service.CompanyService;
import com.stockms.service.GstReportService;
import com.stockms.service.StockService;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * Reports for one branch or all branches the user may access ({@code branch=all}). Without a
 * {@code branch} parameter they follow the branch selected in the top bar.
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

    /** Bills of one branch in a sales or purchase report. */
    public record BranchTotal(Branch branch, int bills, BigDecimal total, BigDecimal paid, BigDecimal due) {
    }

    /** Stock held by one branch. */
    public record BranchStockTotal(Branch branch, int units, BigDecimal value, long lowCount) {
    }

    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;
    private final GstReportService gstReportService;
    private final StockService stockService;
    private final CompanyService companyService;

    public ReportController(SaleRepository saleRepository, PurchaseRepository purchaseRepository,
                            ProductRepository productRepository, BranchRepository branchRepository,
                            GstReportService gstReportService, StockService stockService,
                            CompanyService companyService) {
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.branchRepository = branchRepository;
        this.gstReportService = gstReportService;
        this.stockService = stockService;
        this.companyService = companyService;
    }

    @GetMapping("/sales")
    String sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                 @RequestParam(required = false) String branch,
                 Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        List<Sale> rows = saleRepository.findByBranchIdInAndDateBetweenOrderByDateAscIdAsc(branchScope(branch, model), start, end);
        model.addAttribute("rows", rows);
        model.addAttribute("total", sum(rows, Sale::getSubtotal));
        model.addAttribute("paid", sum(rows, Sale::getPaid));
        model.addAttribute("due", sum(rows, Sale::getDue));
        model.addAttribute("branchTotals", branchTotals(rows, Sale::getBranch, Sale::getSubtotal, Sale::getPaid, Sale::getDue));
        model.addAttribute("isSale", true);
        return dateRange(model, start, end, "Sales Report", "sales");
    }

    @GetMapping("/purchases")
    String purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                     @RequestParam(required = false) String branch,
                     Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        List<Purchase> rows = purchaseRepository.findByBranchIdInAndDateBetweenOrderByDateAscIdAsc(branchScope(branch, model), start, end);
        model.addAttribute("rows", rows);
        model.addAttribute("total", sum(rows, Purchase::getSubtotal));
        model.addAttribute("paid", sum(rows, Purchase::getPaid));
        model.addAttribute("due", sum(rows, Purchase::getDue));
        model.addAttribute("branchTotals", branchTotals(rows, Purchase::getBranch, Purchase::getSubtotal, Purchase::getPaid, Purchase::getDue));
        model.addAttribute("isSale", false);
        return dateRange(model, start, end, "Purchase Report", "purchases");
    }

    @GetMapping("/gst")
    String gst(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
               @RequestParam(required = false) String branch,
               Model model) {
        LocalDate start = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate end = to != null ? to : LocalDate.now();
        model.addAttribute("report", gstReportService.report(branchScope(branch, model), start, end));
        model.addAttribute("company", companyService.current());
        return "reports/gst";
    }

    @GetMapping("/stock")
    String stock(@RequestParam(required = false) String branch, Model model) {
        List<Long> scope = branchScope(branch, model);
        List<Product> products = productRepository.findAllByOrderByNameAsc();
        Map<Long, Integer> totals = stockService.totals(scope);
        products.forEach(p -> p.setQuantity(totals.getOrDefault(p.getId(), 0)));

        // Per-branch columns and summary when more than one branch is shown.
        Map<Long, Map<Long, Integer>> byBranch = stockService.byBranch(scope);
        List<Branch> branches = branchRepository.findAllById(scope).stream()
                .sorted(Comparator.comparing(Branch::isHeadOffice).reversed().thenComparing(Branch::getName)).toList();
        List<BranchStockTotal> branchTotals = new ArrayList<>();
        for (Branch b : branches) {
            Map<Long, Integer> qty = byBranch.getOrDefault(b.getId(), Map.of());
            int units = 0;
            BigDecimal value = BigDecimal.ZERO;
            long low = 0;
            for (Product p : products) {
                int q = qty.getOrDefault(p.getId(), 0);
                units += q;
                value = value.add(p.getCostPrice().multiply(BigDecimal.valueOf(q)));
                if (q <= p.getReorderLevel()) low++;
            }
            branchTotals.add(new BranchStockTotal(b, units, value, low));
        }

        model.addAttribute("products", products);
        model.addAttribute("branches", branches);
        model.addAttribute("byBranch", byBranch);
        model.addAttribute("branchTotals", branchTotals);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("totalValue", sum(products, Product::getStockValue));
        model.addAttribute("totalUnits", products.stream().mapToInt(Product::getQuantity).sum());
        model.addAttribute("lowCount", products.stream().filter(Product::isLowStock).count());
        return "reports/stock";
    }

    /**
     * Branch ids a report covers: one branch the user may access, or all of them for "all".
     * Also puts the choices for the branch filter in the model.
     */
    private List<Long> branchScope(String branch, Model model) {
        Tenant tenant = TenantContext.require();
        Long selected;
        if (branch == null || branch.isBlank()) {
            selected = tenant.branchId();
        } else if (branch.equals("all")) {
            selected = null;
        } else {
            try {
                selected = Long.valueOf(branch);
            } catch (NumberFormatException e) {
                throw new NotFoundException();
            }
            if (!tenant.canAccess(selected)) throw new NotFoundException();
        }
        List<Branch> choices = branchRepository.findAllByOrderByHeadOfficeDescNameAsc().stream()
                .filter(b -> tenant.canAccess(b.getId())).toList();
        model.addAttribute("reportBranches", choices);
        model.addAttribute("reportBranch", selected == null ? null
                : choices.stream().filter(b -> b.getId().equals(selected)).findFirst().orElse(null));
        model.addAttribute("reportBranchParam", selected == null ? "all" : selected.toString());
        return selected == null ? tenant.withBranch(null).scopeForQuery() : List.of(selected);
    }

    private static <T> List<BranchTotal> branchTotals(List<T> rows, Function<T, Branch> branch,
                                                      Function<T, BigDecimal> total, Function<T, BigDecimal> paid,
                                                      Function<T, BigDecimal> due) {
        Map<Long, BranchTotal> totals = new LinkedHashMap<>();
        for (T row : rows) {
            Branch b = branch.apply(row);
            totals.merge(b.getId(), new BranchTotal(b, 1, total.apply(row), paid.apply(row), due.apply(row)),
                    (x, y) -> new BranchTotal(x.branch(), x.bills() + y.bills(), x.total().add(y.total()),
                            x.paid().add(y.paid()), x.due().add(y.due())));
        }
        return totals.values().stream().sorted(Comparator.comparing(t -> t.branch().getName())).toList();
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
