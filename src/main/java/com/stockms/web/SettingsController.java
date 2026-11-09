package com.stockms.web;

import com.stockms.entity.CompanySettings;
import com.stockms.service.Gst;
import com.stockms.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

/** Company details printed on invoices; restricted to admins in SecurityConfig. */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** The logo only changes through the upload field, never through plain form binding. */
    @InitBinder("company")
    void protectLogo(WebDataBinder binder) {
        binder.setDisallowedFields("logo", "logoType");
    }

    @ModelAttribute("states")
    java.util.List<String> states() {
        return Gst.stateNames();
    }

    @GetMapping
    String edit(Model model) {
        model.addAttribute("company", settingsService.get());
        return "settings";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("company") CompanySettings company, BindingResult result,
                @RequestParam(required = false) MultipartFile logoFile,
                @RequestParam(defaultValue = "false") boolean removeLogo,
                RedirectAttributes redirect) throws IOException {
        byte[] logo = logoFile == null || logoFile.isEmpty() ? null : logoFile.getBytes();
        if (logo != null && logo.length > SettingsService.MAX_LOGO_BYTES) {
            result.rejectValue("logo", "size", "Logo must be 500 KB or smaller.");
        } else if (logo != null && SettingsService.imageType(logo) == null) {
            result.rejectValue("logo", "type", "Logo must be a PNG, JPEG, GIF or WebP image.");
        }
        if (result.hasErrors()) {
            CompanySettings current = settingsService.get();
            company.setLogo(current.getLogo());
            company.setLogoType(current.getLogoType());
            return "settings";
        }
        settingsService.save(company, logo, removeLogo);
        redirect.addFlashAttribute("success", "Company settings saved.");
        return "redirect:/settings";
    }
}
