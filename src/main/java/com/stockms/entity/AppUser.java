package com.stockms.entity;

import jakarta.persistence.*;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A login. Not filtered by company like the business data, because sign-in looks users up
 * by name before any company is known; screens listing users scope them by company themselves.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    public enum Role {
        /** Manages companies; sees no company's business data. */
        SUPER_ADMIN("Super admin"),
        /** Everything in their own company, all branches. */
        COMPANY_ADMIN("Company admin"),
        BRANCH_MANAGER("Branch manager"),
        STAFF("Staff");

        private final String label;

        Role(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt hash. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.STAFF;

    /** Null only for a super admin. */
    @ManyToOne
    private Company company;

    /** Branches a branch manager or staff member may work in; company admins may use all. */
    @ManyToMany
    @JoinTable(name = "user_branch",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id"))
    private Set<Branch> branches = new LinkedHashSet<>();

    public AppUser() {
    }

    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public boolean isCompanyAdmin() {
        return role == Role.COMPANY_ADMIN;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Set<Branch> getBranches() { return branches; }
}
