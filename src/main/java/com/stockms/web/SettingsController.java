package com.stockms.web;

import com.stockms.entity.CompanySettings;
import com.stockms.service.Gst;
import com.stockms.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Company details printed on invoices; restricted to admins in SecurityConfig. */
@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
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
                RedirectAttributes redirect) {
        if (result.hasErrors()) return "settings";
        settingsService.save(company);
        redirect.addFlashAttribute("success", "Company settings saved.");
        return "redirect:/settings";
    }
}
