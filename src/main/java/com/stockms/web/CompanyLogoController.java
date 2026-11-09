package com.stockms.web;

import com.stockms.entity.CompanySettings;
import com.stockms.service.SettingsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the company logo to any signed-in user, since staff print invoices too. */
@Controller
public class CompanyLogoController {

    private final SettingsService settingsService;

    public CompanyLogoController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/company-logo")
    ResponseEntity<byte[]> logo() {
        CompanySettings company = settingsService.get();
        if (!company.hasLogo()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(company.getLogoType()))
                .body(company.getLogo());
    }
}
