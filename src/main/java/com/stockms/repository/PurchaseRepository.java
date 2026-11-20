package com.stockms.repository;

import com.stockms.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    List<Purchase> findByBranchIdInOrderByDateDescIdDesc(Collection<Long> branchIds);

    List<Purchase> findTop5ByBranchIdInOrderByIdDesc(Collection<Long> branchIds);

    List<Purchase> findByBranchIdInAndDateBetweenOrderByDateAscIdAsc(Collection<Long> branchIds, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(x.subtotal), 0) from Purchase x where x.branch.id in :branchIds and x.date between :from and :to")
    BigDecimal totalBetween(Collection<Long> branchIds, LocalDate from, LocalDate to);

    /** Bills saved before GST was added have no tax, so their whole amount is taxable value. */
    @Modifying
    @Query("update Purchase x set x.taxableAmount = x.subtotal where x.taxableAmount = 0 and x.subtotal <> 0"
            + " and x.cgstAmount = 0 and x.sgstAmount = 0 and x.igstAmount = 0")
    int backfillTaxableAmount();
}
