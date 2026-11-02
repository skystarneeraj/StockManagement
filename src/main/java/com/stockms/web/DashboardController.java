package com.stockms.web;

import com.stockms.entity.Product;
import com.stockms.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    public DashboardController(ProductRepository productRepository, SaleRepository saleRepository,
                               PurchaseRepository purchaseRepository, CustomerRepository customerRepository,
                               SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping("/")
    String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Product> products = productRepository.findAll();

        model.addAttribute("productCount", products.size());
        model.addAttribute("stockValue", products.stream().map(Product::getStockValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("salesToday", saleRepository.totalBetween(today, today));
        model.addAttribute("salesMonth", saleRepository.totalBetween(monthStart, today));
        model.addAttribute("purchasesMonth", purchaseRepository.totalBetween(monthStart, today));
        model.addAttribute("receivable", customerRepository.totalBalance());
        model.addAttribute("payable", supplierRepository.totalBalance());
        model.addAttribute("lowStock", productRepository.findLowStock());
        model.addAttribute("recentSales", saleRepository.findTop5ByOrderByIdDesc());
        return "dashboard";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }
}
