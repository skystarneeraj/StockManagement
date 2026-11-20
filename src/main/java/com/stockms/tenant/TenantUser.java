package com.stockms.tenant;

import com.stockms.entity.AppUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.List;

/** The signed-in principal: a Spring Security user that also knows its id and company. */
public class TenantUser extends User {

    private final Long userId;
    private final Long companyId;
    private final AppUser.Role role;

    public TenantUser(AppUser user) {
        super(user.getUsername(), user.getPassword(),
                user.getCompany() == null || user.getCompany().isActive(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.userId = user.getId();
        this.companyId = user.getCompany() == null ? null : user.getCompany().getId();
        this.role = user.getRole();
    }

    public Long getUserId() { return userId; }

    /** Null for a super admin. */
    public Long getCompanyId() { return companyId; }

    public AppUser.Role getRole() { return role; }
}
