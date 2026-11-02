package com.stockms.repository;

import com.stockms.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByNameAsc();

    Optional<Product> findByCodeIgnoreCase(String code);

    @Query("""
            select p from Product p
            where lower(p.name) like lower(concat('%', :q, '%'))
               or lower(p.code) like lower(concat('%', :q, '%'))
            order by p.name""")
    List<Product> search(String q);

    @Query("select p from Product p where p.quantity <= p.reorderLevel order by p.quantity, p.name")
    List<Product> findLowStock();
}
