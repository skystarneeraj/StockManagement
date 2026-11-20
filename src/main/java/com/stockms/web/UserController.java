package com.stockms.web;

import com.stockms.entity.AppUser;
import com.stockms.entity.Branch;
import com.stockms.repository.AppUserRepository;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.CompanyRepository;
import com.stockms.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Users of the signed-in admin's company; restricted to company admins in SecurityConfig.
 * Users are not company-filtered by Hibernate, so every lookup here passes the company.
 */
@Controller
@RequestMapping("/users")
public class UserController {

    /** Roles a company admin may give; super admins are managed outside a company. */
    static final List<AppUser.Role> ROLES =
            List.of(AppUser.Role.COMPANY_ADMIN, AppUser.Role.BRANCH_MANAGER, AppUser.Role.STAFF);

    public static class UserForm {
        private Long id;

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, dot, dash or underscore only")
        private String username;

        /** Required for new users; blank on edit keeps the current password. */
        @Size(max = 100)
        private String password;

        @NotNull
        private AppUser.Role role = AppUser.Role.STAFF;

        /** Branches a branch manager or staff member may work in. */
        private Set<Long> branchIds = new LinkedHashSet<>();

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public AppUser.Role getRole() { return role; }
        public void setRole(AppUser.Role role) { this.role = role; }
        public Set<Long> getBranchIds() { return branchIds; }
        public void setBranchIds(Set<Long> branchIds) { this.branchIds = branchIds == null ? new LinkedHashSet<>() : branchIds; }
    }

    private final AppUserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository userRepository, BranchRepository branchRepository,
                          CompanyRepository companyRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @ModelAttribute
    void choices(Model model) {
        model.addAttribute("roles", ROLES);
        model.addAttribute("branches", branchRepository.findAllByOrderByHeadOfficeDescNameAsc());
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("users", userRepository.findByCompanyIdOrderByUsernameAsc(TenantContext.require().companyId()));
        return "users/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("user", new UserForm());
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        AppUser user = findInCompany(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setRole(user.getRole());
        user.getBranches().forEach(b -> form.getBranchIds().add(b.getId()));
        model.addAttribute("user", form);
        return "users/form";
    }

    @PostMapping
    @Transactional
    String save(@Valid @ModelAttribute("user") UserForm form, BindingResult result,
                Principal principal, RedirectAttributes redirect) {
        boolean isNew = form.getId() == null;
        String password = form.getPassword() == null ? "" : form.getPassword();
        if (isNew && password.length() < 4) {
            result.rejectValue("password", "required", "must be at least 4 characters");
        } else if (!isNew && !password.isEmpty() && password.length() < 4) {
            result.rejectValue("password", "short", "must be at least 4 characters");
        }
        if (form.getUsername() != null) {
            userRepository.findByUsernameIgnoreCase(form.getUsername())
                    .filter(existing -> !existing.getId().equals(form.getId()))
                    .ifPresent(existing -> result.rejectValue("username", "duplicate", "is already taken"));
        }
        if (form.getRole() != null && !ROLES.contains(form.getRole())) {
            result.rejectValue("role", "invalid", "is not allowed");
        }
        // Only branches of this company can be assigned (others are not found through the company filter).
        List<Branch> branches = branchRepository.findAllById(form.getBranchIds());
        if (form.getRole() != AppUser.Role.COMPANY_ADMIN && branches.isEmpty()) {
            result.rejectValue("branchIds", "required", "Select at least one branch.");
        }
        if (result.hasErrors()) return "users/form";

        AppUser user = isNew ? new AppUser() : findInCompany(form.getId());
        if (!isNew && user.getUsername().equalsIgnoreCase(principal.getName()) && form.getRole() != AppUser.Role.COMPANY_ADMIN) {
            redirect.addFlashAttribute("error", "You cannot remove your own admin role.");
            return "redirect:/users";
        }
        user.setUsername(form.getUsername());
        user.setRole(form.getRole());
        user.setCompany(companyRepository.getReferenceById(TenantContext.require().companyId()));
        user.getBranches().clear();
        user.getBranches().addAll(branches);
        if (!password.isEmpty()) user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        redirect.addFlashAttribute("success", "User '" + user.getUsername() + "' saved.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        AppUser user = findInCompany(id);
        if (user.getUsername().equalsIgnoreCase(principal.getName())) {
            redirect.addFlashAttribute("error", "You cannot delete your own account.");
        } else {
            userRepository.delete(user);
            redirect.addFlashAttribute("success", "User '" + user.getUsername() + "' deleted.");
        }
        return "redirect:/users";
    }

    private AppUser findInCompany(Long id) {
        return userRepository.findByIdAndCompanyId(id, TenantContext.require().companyId())
                .orElseThrow(NotFoundException::new);
    }
}
