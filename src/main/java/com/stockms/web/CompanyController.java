package com.stockms.web;

import com.stockms.entity.Company;
import com.stockms.repository.AppUserRepository;
import com.stockms.service.BusinessException;
import com.stockms.service.CompanyService;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/** Companies using the system; super admin only (see SecurityConfig). */
@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final AppUserRepository userRepository;

    public CompanyController(CompanyService companyService, AppUserRepository userRepository) {
        this.companyService = companyService;
        this.userRepository = userRepository;
    }

    @InitBinder("company")
    void protectFields(WebDataBinder binder) {
        binder.setDisallowedFields("logo", "logoType", "active", "createdAt", "allowNegativeStock");
    }

    @ModelAttribute("states")
    java.util.List<String> states() {
        return Gst.stateNames();
    }

    @GetMapping
    String list(Model model) {
        Map<Company, Long> companies = new LinkedHashMap<>();
        companyService.all().forEach(c -> companies.put(c, userRepository.countByCompanyId(c.getId())));
        model.addAttribute("companies", companies);
        return "companies/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        Company company = new Company();
        company.setName(null);
        model.addAttribute("company", company);
        return "companies/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("company", companyService.get(id));
        return "companies/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("company") Company company, BindingResult result,
                @RequestParam(required = false) String adminUsername,
                @RequestParam(required = false) String adminPassword,
                Model model, RedirectAttributes redirect) {
        boolean isNew = company.getId() == null;
        if (companyService.codeTaken(company.getCode(), company.getId())) {
            result.rejectValue("code", "duplicate", "is already used by another company");
        }
        if (isNew && (adminUsername == null || !adminUsername.matches("[A-Za-z0-9._-]{3,50}"))) {
            model.addAttribute("adminUsernameError", "3-50 letters, digits, dot, dash or underscore");
        }
        if (isNew && (adminPassword == null || adminPassword.length() < 4)) {
            model.addAttribute("adminPasswordError", "must be at least 4 characters");
        }
        model.addAttribute("adminUsername", adminUsername);
        if (result.hasErrors() || model.containsAttribute("adminUsernameError") || model.containsAttribute("adminPasswordError")) {
            return "companies/form";
        }
        try {
            if (isNew) {
                companyService.create(company, adminUsername, adminPassword);
                redirect.addFlashAttribute("success", "Company '" + company.getName() + "' created with Head Office and admin login '"
                        + adminUsername + "'.");
            } else {
                companyService.update(company.getId(), company);
                redirect.addFlashAttribute("success", "Company '" + company.getName() + "' saved.");
            }
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "companies/form";
        }
        return "redirect:/companies";
    }

    @PostMapping("/{id}/active")
    String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        companyService.setActive(id, active);
        redirect.addFlashAttribute("success", "Company " + (active ? "activated." : "deactivated; its users can no longer sign in."));
        return "redirect:/companies";
    }
}
