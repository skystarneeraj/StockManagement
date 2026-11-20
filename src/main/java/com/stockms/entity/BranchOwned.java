package com.stockms.entity;

import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.springframework.security.access.AccessDeniedException;

/**
 * A transaction recorded in one branch. On top of the company filter, Hibernate hides
 * records of branches the user is not allowed to access.
 */
@MappedSuperclass
@FilterDef(name = BranchOwned.BRANCH_FILTER, defaultCondition = "branch_id in (:branchIds)",
        parameters = @ParamDef(name = "branchIds", type = Long.class, resolver = TenantContext.BranchIdsResolver.class),
        autoEnabled = true, applyToLoadByKey = true)
@Filter(name = BranchOwned.BRANCH_FILTER)
public abstract class BranchOwned extends CompanyOwned {

    public static final String BRANCH_FILTER = "branchFilter";

    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id", nullable = false, updatable = false)
    private Branch branch;

    @Override
    protected void checkOwnership() {
        checkBranch(branch, getCompanyId());
    }

    /** A branch used by a record must be of the record's company and open to the current user. */
    static void checkBranch(Branch branch, Long companyId) {
        if (branch == null) throw new IllegalStateException("No branch selected.");
        if (!branch.getCompanyId().equals(companyId)) {
            throw new AccessDeniedException("Branch belongs to another company.");
        }
        Tenant tenant = TenantContext.get();
        if (tenant != null && !tenant.canAccess(branch.getId())) {
            throw new AccessDeniedException("No access to branch " + branch.getCode() + ".");
        }
    }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
}
