package com.stockms.repository;

import com.stockms.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBranchIdInOrderByDateDescIdDesc(Collection<Long> branchIds);
}
