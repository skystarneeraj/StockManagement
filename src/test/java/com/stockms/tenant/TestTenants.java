package com.stockms.tenant;

import com.stockms.entity.Branch;
import com.stockms.entity.Company;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Lets tests act as a signed-in user of a company, as TenantFilter does for web requests. */
public final class TestTenants {

    private TestTenants() {
    }

    /** A company admin working in {@code current}, with access to all of {@code branches}. */
    public static Tenant admin(Company company, Branch current, Branch... branches) {
        return new Tenant(null, "admin", company.getId(), true,
                Arrays.stream(branches).map(Branch::getId).collect(Collectors.toSet()), current.getId());
    }

    public static void set(Tenant tenant) {
        TenantContext.set(tenant);
    }

    public static void clear() {
        TenantContext.clear();
    }
}
