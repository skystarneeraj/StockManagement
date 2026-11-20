package com.stockms.web;

import com.stockms.entity.Company;
import com.stockms.service.CompanyService;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** Company Profile: the signed-in admin's own company details printed on invoices (admins only, see SecurityConfig). */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final CompanyService companyService;

    public SettingsController(CompanyService companyService) {
        this.companyService = companyService;
    }

    /** The logo only changes through the upload field; which company is edited is never taken from the form. */
    @InitBinder("company")
    void protectFields(WebDataBinder binder) {
        binder.setDisallowedFields("id", "logo", "logoType", "active", "createdAt");
    }

    @ModelAttribute("states")
    java.util.List<String> states() {
        return Gst.stateNames();
    }

    @GetMapping
    String edit(Model model) {
        model.addAttribute("company", companyService.current());
        return "settings";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("company") Company company, BindingResult result,
                @RequestParam(required = false) MultipartFile logoFile,
                @RequestParam(defaultValue = "false") boolean removeLogo,
                RedirectAttributes redirect) throws IOException {
        byte[] logo = logoFile == null || logoFile.isEmpty() ? null : logoFile.getBytes();
        if (logo != null && logo.length > CompanyService.MAX_LOGO_BYTES) {
            result.rejectValue("logo", "size", "Logo must be 500 KB or smaller.");
        } else if (logo != null && CompanyService.imageType(logo) == null) {
            result.rejectValue("logo", "type", "Logo must be a PNG, JPEG, GIF or WebP image.");
        }
        Company current = companyService.current();
        if (companyService.codeTaken(company.getCode(), current.getId())) {
            result.rejectValue("code", "duplicate", "is already used by another company");
        }
        if (result.hasErrors()) {
            company.setLogo(current.getLogo());
            company.setLogoType(current.getLogoType());
            return "settings";
        }
        companyService.saveProfile(company, logo, removeLogo);
        redirect.addFlashAttribute("success", "Company profile saved.");
        return "redirect:/settings";
    }
}
