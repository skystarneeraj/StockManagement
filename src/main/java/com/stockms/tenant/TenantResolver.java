package com.stockms.tenant;

import com.stockms.entity.AppUser;
import com.stockms.entity.Branch;
import com.stockms.repository.AppUserRepository;
import com.stockms.repository.BranchRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Works out the {@link Tenant} for a request from the database, not from what the browser sends:
 * the user's company, the branches they may access, and the branch selected in their session.
 * Reading it per request means a deactivated company or a removed branch takes effect at once.
 */
@Component
public class TenantResolver {

    /** Session attribute holding the selected branch id; {@link #ALL_BRANCHES} for "All branches". */
    public static final String SESSION_BRANCH = "stockms.branchId";
    public static final Long ALL_BRANCHES = 0L;

    private final AppUserRepository userRepository;
    private final BranchRepository branchRepository;

    public TenantResolver(AppUserRepository userRepository, BranchRepository branchRepository) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
    }

    /** The tenant, or null when the user or their company may no longer sign in. */
    @Transactional(readOnly = true)
    public Tenant resolve(TenantUser principal, HttpSession session) {
        AppUser user = userRepository.findById(principal.getUserId()).orElse(null);
        if (user == null || user.getCompany() == null || !user.getCompany().isActive()) return null;

        boolean admin = user.isCompanyAdmin();
        Tenant companyOnly = new Tenant(user.getId(), user.getUsername(), user.getCompany().getId(), admin, Set.of(), null);
        return TenantContext.callAs(companyOnly, () -> {
            Set<Long> assigned = admin ? Set.of() : userRepository.findBranchIds(user.getId());
            Set<Long> allowed = new LinkedHashSet<>();
            List<Long> selectable = new ArrayList<>();
            for (Branch branch : branchRepository.findAllByOrderByHeadOfficeDescNameAsc()) {
                if (!admin && !assigned.contains(branch.getId())) continue;
                allowed.add(branch.getId());
                if (branch.isActive()) selectable.add(branch.getId());
            }

            Long selected = (Long) session.getAttribute(SESSION_BRANCH);
            Long branchId;
            if (admin && ALL_BRANCHES.equals(selected)) {
                branchId = null;
            } else if (selected != null && selectable.contains(selected)) {
                branchId = selected;
            } else {
                // First visit, or the selected branch was deactivated or taken away: use the first one open to the user.
                branchId = selectable.isEmpty() ? null : selectable.getFirst();
                session.setAttribute(SESSION_BRANCH, branchId);
            }
            return new Tenant(user.getId(), user.getUsername(), companyOnly.companyId(), admin, allowed, branchId);
        });
    }

    /** Branches the user may switch to: allowed and active. */
    @Transactional(readOnly = true)
    public List<Branch> selectableBranches(Tenant tenant) {
        return branchRepository.findByActiveTrueOrderByHeadOfficeDescNameAsc().stream()
                .filter(b -> tenant.canAccess(b.getId()))
                .toList();
    }
}
