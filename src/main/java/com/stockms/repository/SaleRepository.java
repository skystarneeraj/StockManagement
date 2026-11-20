package com.stockms.repository;

import com.stockms.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByBranchIdInOrderByDateDescIdDesc(Collection<Long> branchIds);

    List<Sale> findTop5ByBranchIdInOrderByIdDesc(Collection<Long> branchIds);

    List<Sale> findByBranchIdInAndDateBetweenOrderByDateAscIdAsc(Collection<Long> branchIds, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(x.subtotal), 0) from Sale x where x.branch.id in :branchIds and x.date between :from and :to")
    BigDecimal totalBetween(Collection<Long> branchIds, LocalDate from, LocalDate to);

    /** Bills saved before GST was added have no tax, so their whole amount is taxable value. */
    @Modifying
    @Query("update Sale x set x.taxableAmount = x.subtotal where x.taxableAmount = 0 and x.subtotal <> 0"
            + " and x.cgstAmount = 0 and x.sgstAmount = 0 and x.igstAmount = 0")
    int backfillTaxableAmount();
}
