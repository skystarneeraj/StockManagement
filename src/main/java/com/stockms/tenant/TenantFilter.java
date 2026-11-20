package com.stockms.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets the {@link TenantContext} for each request of a signed-in company user. Runs after
 * Spring Security, so the authenticated principal is known.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver resolver;

    public TenantFilter(TenantResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantUser user && user.getCompanyId() != null) {
            Tenant tenant = resolver.resolve(user, request.getSession());
            if (tenant == null) {
                // The account was removed or its company deactivated since sign-in.
                request.getSession().invalidate();
                SecurityContextHolder.clearContext();
                response.sendRedirect(request.getContextPath() + "/login?disabled");
                return;
            }
            TenantContext.set(tenant);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
