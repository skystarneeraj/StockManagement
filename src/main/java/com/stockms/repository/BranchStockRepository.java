package com.stockms.repository;

import com.stockms.entity.BranchStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

/**
 * Stock is read with scalar queries and changed with single UPDATE statements, so two users
 * selling the same product at once can never both take the last unit.
 */
public interface BranchStockRepository extends JpaRepository<BranchStock, Long> {

    @Modifying
    @Query(value = "insert into branch_stock (branch_id, product_id, quantity) values (:branchId, :productId, 0)"
            + " on conflict (branch_id, product_id) do nothing", nativeQuery = true)
    void ensureRow(Long branchId, Long productId);

    /** Adds {@code delta} (negative to remove); returns 0 when that would go below zero and it is not allowed. */
    @Modifying(flushAutomatically = true)
    @Query("update BranchStock s set s.quantity = s.quantity + :delta"
            + " where s.branch.id = :branchId and s.product.id = :productId"
            + " and (:allowNegative = true or s.quantity + :delta >= 0)")
    int change(Long branchId, Long productId, int delta, boolean allowNegative);

    @Query("select coalesce(sum(s.quantity), 0) from BranchStock s where s.branch.id = :branchId and s.product.id = :productId")
    int quantity(Long branchId, Long productId);

    /** [productId, total quantity] over the given branches. */
    @Query("select s.product.id, sum(s.quantity) from BranchStock s where s.branch.id in :branchIds group by s.product.id")
    List<Object[]> totalsByProduct(Collection<Long> branchIds);

    /** [branchId, productId, quantity] for the given branches. */
    @Query("select s.branch.id, s.product.id, s.quantity from BranchStock s where s.branch.id in :branchIds")
    List<Object[]> byBranchAndProduct(Collection<Long> branchIds);
}
