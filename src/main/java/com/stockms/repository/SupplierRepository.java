package com.stockms.repository;

import com.stockms.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findAllByOrderByNameAsc();

    List<Supplier> findByBalanceGreaterThanOrderByNameAsc(BigDecimal amount);

    @Query("select coalesce(sum(x.balance), 0) from Supplier x")
    BigDecimal totalBalance();
}
