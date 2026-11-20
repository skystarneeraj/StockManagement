package com.stockms.service;

import com.stockms.entity.Branch;
import com.stockms.entity.Company;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Issues document numbers per branch and financial year (April-March), e.g. KT-DEL-2026-27/0001
 * for an invoice. The counter row stays locked until the saving transaction ends, so concurrent
 * saves in one branch get consecutive numbers, never the same one; a rolled-back save gives its number back.
 */
@Service
public class DocNumberService {

    public enum DocType {
        SALE(""), PURCHASE("PUR-"), TRANSFER("TRF-");

        private final String prefix;

        DocType(String prefix) {
            this.prefix = prefix;
        }
    }

    private final EntityManager entityManager;

    public DocNumberService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Company company, Branch branch, DocType type, LocalDate date) {
        String year = fiscalYear(date);
        Number number = (Number) entityManager.createNativeQuery("""
                        insert into doc_sequences (branch_id, doc_type, fiscal_year, last_number) values (?1, ?2, ?3, 1)
                        on conflict (branch_id, doc_type, fiscal_year)
                        do update set last_number = doc_sequences.last_number + 1
                        returning last_number""")
                .setParameter(1, branch.getId())
                .setParameter(2, type.name())
                .setParameter(3, year)
                .getSingleResult();
        return "%s-%s-%s%s/%04d".formatted(company.getCode(), branch.getCode(), type.prefix, year, number.intValue());
    }

    /** "2026-27" for any date from 1 April 2026 to 31 March 2027. */
    public static String fiscalYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return "%d-%02d".formatted(start, (start + 1) % 100);
    }
}
