package com.stockms.web;

import com.stockms.entity.Product;
import com.stockms.repository.*;
import com.stockms.service.StockService;
import com.stockms.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/** Figures for the branch selected in the top bar, or the whole company under "All branches". */
@Controller
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final StockService stockService;

    public DashboardController(ProductRepository productRepository, SaleRepository saleRepository,
                               PurchaseRepository purchaseRepository, CustomerRepository customerRepository,
                               SupplierRepository supplierRepository, StockService stockService) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.stockService = stockService;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        List<Long> scope = TenantContext.require().scopeForQuery();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Product> products = stockService.fillQuantities(productRepository.findAllByOrderByNameAsc());

        model.addAttribute("productCount", products.size());
        model.addAttribute("stockValue", products.stream().map(Product::getStockValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("salesToday", saleRepository.totalBetween(scope, today, today));
        model.addAttribute("salesMonth", saleRepository.totalBetween(scope, monthStart, today));
        model.addAttribute("purchasesMonth", purchaseRepository.totalBetween(scope, monthStart, today));
        // Customers and suppliers are shared by all branches, so their balances are company-wide.
        model.addAttribute("receivable", customerRepository.totalBalance());
        model.addAttribute("payable", supplierRepository.totalBalance());
        model.addAttribute("lowStock", products.stream().filter(Product::isLowStock)
                .sorted(Comparator.comparingInt(Product::getQuantity).thenComparing(Product::getName)).toList());
        model.addAttribute("recentSales", saleRepository.findTop5ByBranchIdInOrderByIdDesc(scope));
        return "dashboard";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
