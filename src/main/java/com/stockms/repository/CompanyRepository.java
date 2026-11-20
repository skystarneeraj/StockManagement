package com.stockms.repository;

import com.stockms.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findAllByOrderByNameAsc();

    Optional<Company> findByCodeIgnoreCase(String code);
}
