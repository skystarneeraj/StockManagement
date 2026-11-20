package com.stockms.entity;

import com.stockms.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.security.access.AccessDeniedException;

/**
 * Data that belongs to one company. Hibernate limits every query and every load by id to the
 * current company (an always-on filter), and the company is set here when a record is first
 * saved - never taken from a form.
 */
@MappedSuperclass
@FilterDef(name = CompanyOwned.COMPANY_FILTER, defaultCondition = "company_id = :companyId",
        parameters = @ParamDef(name = "companyId", type = Long.class, resolver = TenantContext.CompanyIdResolver.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = CompanyOwned.COMPANY_FILTER)
public abstract class CompanyOwned {

    public static final String COMPANY_FILTER = "companyFilter";

    @Column(name = "company_id", nullable = false, updatable = false)
    private Long companyId;

    @PrePersist
    void assignCompany() {
        Long current = TenantContext.companyId();
        if (current != null) {
            companyId = current;
        } else if (companyId == null) {
            // Only server code working outside a company (e.g. the super admin) may set it explicitly.
            throw new IllegalStateException("No company for new " + getClass().getSimpleName());
        }
        checkOwnership();
    }

    @PreUpdate
    void checkCompanyUnchanged() {
        Long current = TenantContext.companyId();
        if (current != null && !current.equals(companyId)) {
            throw new AccessDeniedException(getClass().getSimpleName() + " belongs to another company.");
        }
        checkOwnership();
    }

    /** Further checks before saving, e.g. that a branch belongs to this company. */
    protected void checkOwnership() {
    }

    public Long getCompanyId() { return companyId; }

    /** For server code creating data in a company other than the signed-in user's (super admin). */
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
}
