package com.stockms.repository;

import com.stockms.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Branches of the current company only (company filter). */
public interface BranchRepository extends JpaRepository<Branch, Long> {

    List<Branch> findAllByOrderByHeadOfficeDescNameAsc();

    List<Branch> findByActiveTrueOrderByHeadOfficeDescNameAsc();

    Optional<Branch> findByCodeIgnoreCase(String code);
}
