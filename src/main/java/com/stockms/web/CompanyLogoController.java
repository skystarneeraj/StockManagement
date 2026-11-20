package com.stockms.web;

import com.stockms.entity.Company;
import com.stockms.service.CompanyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the signed-in user's company logo to any of its users, since staff print invoices too. */
@Controller
public class CompanyLogoController {

    private final CompanyService companyService;

    public CompanyLogoController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping("/company-logo")
    ResponseEntity<byte[]> logo() {
        Company company = companyService.current();
        if (!company.hasLogo()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(company.getLogoType()))
                .body(company.getLogo());
    }
}
