package com.stockms.repository;

import com.stockms.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrderByNameAsc();

    List<Customer> findByBalanceGreaterThanOrderByNameAsc(BigDecimal amount);

    @Query("select coalesce(sum(x.balance), 0) from Customer x")
    BigDecimal totalBalance();
}
