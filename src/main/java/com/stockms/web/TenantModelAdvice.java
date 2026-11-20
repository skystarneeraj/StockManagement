package com.stockms.web;

import com.stockms.repository.BranchRepository;
import com.stockms.service.CompanyService;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import com.stockms.tenant.TenantResolver;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Company name, current branch and the branch switcher shown in the top bar of every page. */
@ControllerAdvice
public class TenantModelAdvice {

    private final CompanyService companyService;
    private final BranchRepository branchRepository;
    private final TenantResolver tenantResolver;

    public TenantModelAdvice(CompanyService companyService, BranchRepository branchRepository,
                             TenantResolver tenantResolver) {
        this.companyService = companyService;
        this.branchRepository = branchRepository;
        this.tenantResolver = tenantResolver;
    }

    @ModelAttribute
    void tenant(Model model) {
        Tenant tenant = TenantContext.get();
        if (tenant == null || tenant.companyId() == null) return;
        model.addAttribute("currentCompany", companyService.current());
        model.addAttribute("currentBranch", tenant.branchId() == null ? null
                : branchRepository.findById(tenant.branchId()).orElse(null));
        model.addAttribute("switchBranches", tenantResolver.selectableBranches(tenant));
        model.addAttribute("canSeeAllBranches", tenant.companyAdmin());
    }
}
