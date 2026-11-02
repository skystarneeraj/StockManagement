package com.stockms.config;

import com.stockms.entity.Category;
import com.stockms.entity.Customer;
import com.stockms.entity.Supplier;
import com.stockms.entity.Uom;
import com.stockms.repository.CategoryRepository;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.repository.UomRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets forms submit an entity id (from a select box) and receive the entity. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;

    public WebConfig(CategoryRepository categoryRepository, UomRepository uomRepository,
                     SupplierRepository supplierRepository, CustomerRepository customerRepository) {
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, Category.class, id -> find(categoryRepository, id));
        registry.addConverter(String.class, Uom.class, id -> find(uomRepository, id));
        registry.addConverter(String.class, Supplier.class, id -> find(supplierRepository, id));
        registry.addConverter(String.class, Customer.class, id -> find(customerRepository, id));
    }

    private static <T> T find(JpaRepository<T, Long> repository, String id) {
        if (id == null || id.isBlank()) return null;
        return repository.findById(Long.valueOf(id)).orElse(null);
    }
}
