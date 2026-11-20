package com.stockms.repository;

import com.stockms.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    @Query("select t from StockTransfer t where t.fromBranch.id in :branchIds or t.toBranch.id in :branchIds"
            + " order by t.date desc, t.id desc")
    List<StockTransfer> findInvolving(Collection<Long> branchIds);
}
