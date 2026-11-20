package com.stockms.tenant;

import org.springframework.security.access.AccessDeniedException;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * The {@link Tenant} of the current request (set by {@link TenantFilter}) or of a block of
 * background work ({@link #callAs}). Hibernate's company and branch filters read it through
 * the resolvers below, so every query is limited to the current company and allowed branches.
 * With no tenant those filters match nothing.
 */
public final class TenantContext {

    private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

    /** Filter value when there is no tenant; no row has this id. */
    private static final Long NONE = -1L;

    private TenantContext() {
    }

    /** The current tenant, or null outside a signed-in company user's request. */
    public static Tenant get() {
        return CURRENT.get();
    }

    public static Tenant require() {
        Tenant tenant = CURRENT.get();
        if (tenant == null || tenant.companyId() == null) throw new AccessDeniedException("No company selected.");
        return tenant;
    }

    public static Long companyId() {
        Tenant tenant = CURRENT.get();
        return tenant == null ? null : tenant.companyId();
    }

    static void set(Tenant tenant) {
        CURRENT.set(tenant);
    }

    static void clear() {
        CURRENT.remove();
    }

    public static <T> T callAs(Tenant tenant, Supplier<T> work) {
        Tenant previous = CURRENT.get();
        CURRENT.set(tenant);
        try {
            return work.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void runAs(Tenant tenant, Runnable work) {
        callAs(tenant, () -> {
            work.run();
            return null;
        });
    }

    /** Supplies {@code :companyId} to the Hibernate company filter. */
    public static final class CompanyIdResolver implements Supplier<Long> {
        @Override
        public Long get() {
            Long id = companyId();
            return id == null ? NONE : id;
        }
    }

    /** Supplies {@code :branchIds} to the Hibernate branch filter: every branch the user may access. */
    public static final class BranchIdsResolver implements Supplier<Collection<Long>> {
        @Override
        public Collection<Long> get() {
            Tenant tenant = CURRENT.get();
            return tenant == null ? List.of(NONE) : Tenant.forQuery(tenant.allowedBranchIds());
        }
    }
}
