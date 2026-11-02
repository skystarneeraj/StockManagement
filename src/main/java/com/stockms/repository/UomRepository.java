package com.stockms.repository;

import com.stockms.entity.Uom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    List<Uom> findAllByOrderByNameAsc();

    Optional<Uom> findByNameIgnoreCase(String name);
}
