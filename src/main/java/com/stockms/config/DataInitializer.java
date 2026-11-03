package com.stockms.config;

import com.stockms.entity.*;
import com.stockms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Creates the default admin account and some sample data on first start. */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AppUserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final PurchaseRepository purchaseRepository;
    private final CompanySettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataInitializer(AppUserRepository userRepository, CategoryRepository categoryRepository,
                           UomRepository uomRepository, SupplierRepository supplierRepository,
                           CustomerRepository customerRepository, ProductRepository productRepository,
                           SaleRepository saleRepository, PurchaseRepository purchaseRepository,
                           CompanySettingsRepository settingsRepository, PasswordEncoder passwordEncoder,
                           @Value("${app.admin-password:admin}") String adminPassword) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.purchaseRepository = purchaseRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() == 0) {
            userRepository.save(new AppUser("admin", passwordEncoder.encode(adminPassword), AppUser.Role.ADMIN));
            log.warn("Created default admin login - change its password under Users after signing in.");
        }
        if (!settingsRepository.existsById(CompanySettings.ID)) {
            settingsRepository.save(new CompanySettings());
        }
        int updated = saleRepository.backfillTaxableAmount() + purchaseRepository.backfillTaxableAmount();
        if (updated > 0) log.info("Set taxable value on {} bills saved before GST support.", updated);
        if (productRepository.count() > 0) return;

        Category electronics = categoryRepository.save(new Category("Electronics", "Electronic items and accessories"));
        Category grocery = categoryRepository.save(new Category("Grocery", "Food and household items"));
        Category stationery = categoryRepository.save(new Category("Stationery", "Office and school supplies"));

        Uom pcs = uomRepository.save(new Uom("Pcs", "Pieces"));
        Uom kg = uomRepository.save(new Uom("Kg", "Kilograms"));
        Uom box = uomRepository.save(new Uom("Box", "Box / carton"));

        Supplier supplier = supplierRepository.save(new Supplier("ABC Distributors", "12 Market Road", "9876543210"));
        customerRepository.save(new Customer("Walk-in Customer", "", ""));
        customerRepository.save(new Customer("Sri Ram Stores", "45 Main Street", "9123456780"));

        productRepository.save(product("P001", "USB Mouse", "8471", "18", electronics, pcs, supplier, "250", "350", 40, 10));
        productRepository.save(product("P002", "Keyboard", "8471", "18", electronics, pcs, supplier, "450", "600", 25, 5));
        productRepository.save(product("P003", "Basmati Rice", "1006", "5", grocery, kg, supplier, "80", "95", 200, 50));
        productRepository.save(product("P004", "A4 Paper Ream", "4802", "18", stationery, box, supplier, "220", "280", 3, 10));
        log.info("Sample categories, units, parties and products created.");
    }

    private static Product product(String code, String name, String hsn, String gstRate,
                                   Category category, Uom uom, Supplier supplier,
                                   String cost, String price, int quantity, int reorderLevel) {
        Product p = new Product();
        p.setCode(code);
        p.setName(name);
        p.setHsnCode(hsn);
        p.setGstRate(new BigDecimal(gstRate));
        p.setCategory(category);
        p.setUom(uom);
        p.setSupplier(supplier);
        p.setCostPrice(new BigDecimal(cost));
        p.setSellingPrice(new BigDecimal(price));
        p.setQuantity(quantity);
        p.setReorderLevel(reorderLevel);
        if (category.getName().equals("Grocery")) p.setExpiryDate(LocalDate.now().plusMonths(6));
        return p;
    }
}
