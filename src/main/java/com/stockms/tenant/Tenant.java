package com.stockms.tenant;

import com.stockms.service.BusinessException;

import java.util.List;
import java.util.Set;

/**
 * Who is working and on what: the signed-in user's company, the branches they may access,
 * and the branch selected in the top bar ({@code branchId} null means "All branches").
 */
public record Tenant(Long userId, String username, Long companyId, boolean companyAdmin,
                     Set<Long> allowedBranchIds, Long branchId) {

    public Tenant {
        allowedBranchIds = Set.copyOf(allowedBranchIds);
    }

    /** A tenant for background work in one company, e.g. creating sample data. */
    public static Tenant system(Long companyId, Set<Long> branchIds, Long branchId) {
        return new Tenant(null, "system", companyId, true, branchIds, branchId);
    }

    public boolean canAccess(Long branchId) {
        return branchId != null && allowedBranchIds.contains(branchId);
    }

    /** Branches whose data is shown: the selected one, or all allowed ones. */
    public Set<Long> scopeBranchIds() {
        return branchId == null ? allowedBranchIds : Set.of(branchId);
    }

    /** {@link #scopeBranchIds()} safe for an SQL "in" list, which cannot be empty. */
    public List<Long> scopeForQuery() {
        return forQuery(scopeBranchIds());
    }

    /** The branch a new sale, purchase, payment or transfer belongs to. */
    public Long requireBranchId() {
        if (branchId == null) throw new BusinessException("Choose a branch in the top bar first.");
        return branchId;
    }

    public Tenant withBranch(Long branchId) {
        return new Tenant(userId, username, companyId, companyAdmin, allowedBranchIds, branchId);
    }

    static List<Long> forQuery(Set<Long> ids) {
        return ids.isEmpty() ? List.of(-1L) : List.copyOf(ids);
    }
}
