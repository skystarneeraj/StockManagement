package com.stockms.web;

import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import com.stockms.tenant.TenantResolver;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;

/** The branch dropdown in the top bar. Only branches open to the user can be chosen. */
@Controller
public class BranchSwitchController {

    private final TenantResolver tenantResolver;

    public BranchSwitchController(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @PostMapping("/branch/switch")
    String switchBranch(@RequestParam Long branchId,
                        @RequestHeader(value = "Referer", required = false) String referer,
                        HttpSession session) {
        Tenant tenant = TenantContext.require();
        boolean allowed = TenantResolver.ALL_BRANCHES.equals(branchId)
                ? tenant.companyAdmin()
                : tenantResolver.selectableBranches(tenant).stream().anyMatch(b -> b.getId().equals(branchId));
        if (!allowed) throw new AccessDeniedException("No access to branch " + branchId);
        session.setAttribute(TenantResolver.SESSION_BRANCH, branchId);
        return "redirect:" + backTo(referer);
    }

    /** The page the switch was made from, as a path on this site (never another host). */
    static String backTo(String referer) {
        try {
            URI uri = URI.create(referer);
            String path = uri.getRawPath();
            if (path == null || !path.startsWith("/") || path.startsWith("//")) return "/";
            return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
        } catch (RuntimeException e) {
            return "/";
        }
    }
}
