package com.stockms;

import com.stockms.repository.AppUserRepository;
import com.stockms.repository.CompanyRepository;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders every screen so template mistakes fail the build. Signs in as the sample company's admin. */
@StockMsTest
@AutoConfigureMockMvc
class WebPagesTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired AppUserRepository userRepository;

    /** Repository reads in a test run outside a request, so they need the company set. */
    private <T> T asSampleCompany(java.util.function.Supplier<T> read) {
        Long companyId = companyRepository.findByCodeIgnoreCase("MC").orElseThrow().getId();
        return TenantContext.callAs(Tenant.system(companyId, Set.of(), null), read);
    }

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/products", "/products?low=true", "/products?q=mouse", "/products/new",
            "/categories", "/categories/new", "/uoms", "/suppliers", "/suppliers/new",
            "/customers", "/sales", "/sales/new", "/purchases", "/purchases/new",
            "/payments", "/payments/new", "/payments/new?type=SUPPLIER_PAYMENT",
            "/transfers", "/transfers/new",
            "/reports/sales", "/reports/sales?branch=all", "/reports/purchases?from=2020-01-01&to=2030-12-31",
            "/reports/stock", "/reports/stock?branch=all", "/reports/gst", "/reports/gst?branch=all&from=2020-01-01&to=2030-12-31",
            "/settings", "/branches", "/branches/new", "/users", "/users/new"})
    @WithUserDetails("admin")
    void pageRenders(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("admin")
    void editPagesRender() throws Exception {
        Long productId = asSampleCompany(() -> productRepository.findByCodeIgnoreCase("P001").orElseThrow().getId());
        Long customerId = asSampleCompany(() -> customerRepository.findAll().getFirst().getId());
        Long userId = userRepository.findByUsernameIgnoreCase("admin").orElseThrow().getId();
        mvc.perform(get("/products/{id}/edit", productId)).andExpect(status().isOk());
        mvc.perform(get("/customers/{id}/edit", customerId)).andExpect(status().isOk());
        mvc.perform(get("/users/{id}/edit", userId)).andExpect(status().isOk());
    }

    @Test
    @WithUserDetails("admin")
    void headerShowsCompanyAndBranch() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("My Company")))
                .andExpect(content().string(containsString("Head Office")))
                .andExpect(content().string(containsString("All branches")));
    }

    @Test
    @WithUserDetails("superadmin")
    void superAdminManagesCompaniesButSeesNoCompanyData() throws Exception {
        mvc.perform(get("/companies")).andExpect(status().isOk())
                .andExpect(content().string(containsString("My Company")));
        mvc.perform(get("/companies/new")).andExpect(status().isOk());
        mvc.perform(get("/products")).andExpect(status().isForbidden());
        mvc.perform(get("/")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void companyAdminCannotManageCompanies() throws Exception {
        mvc.perform(get("/companies")).andExpect(status().isForbidden());
    }

    @Test
    @WithUserDetails("admin")
    void saleCanBeEnteredThroughTheFormAndPrinted() throws Exception {
        Long productId = asSampleCompany(() -> productRepository.findByCodeIgnoreCase("P001").orElseThrow().getId());
        Long customerId = asSampleCompany(() -> customerRepository.findAll().getFirst().getId());

        String location = mvc.perform(post("/sales").with(csrf())
                        .param("partyId", customerId.toString())
                        .param("date", "2026-01-15")
                        .param("lines[0].productId", productId.toString())
                        .param("lines[0].quantity", "1")
                        .param("lines[0].price", "350")
                        .param("paid", "350")
                        .param("mode", "Cash"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(content().string(containsString("USB Mouse")))
                .andExpect(content().string(containsString("TAX INVOICE")))
                .andExpect(content().string(containsString("MC-HO-2025-26/")))
                .andExpect(content().string(containsString("8471")));
    }

    @Test
    @WithUserDetails("admin")
    void invalidGstinIsRejectedAndValidOneFillsState() throws Exception {
        mvc.perform(post("/customers").with(csrf()).param("name", "Bad GST Co").param("gstin", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("not a valid 15-character GSTIN")));

        mvc.perform(post("/customers").with(csrf()).param("name", "Good GST Co").param("gstin", "33abcde1234f1z5")
                        .param("state", ""))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(asSampleCompany(() -> customerRepository.findAll()))
                .anyMatch(c -> "33ABCDE1234F1Z5".equals(c.getGstin()) && "Tamil Nadu".equals(c.getState()));
    }

    @Test
    @WithUserDetails("admin")
    void logoCanBeUploadedKeptAndRemoved() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

        mvc.perform(multipart("/settings").file(new MockMultipartFile("logoFile", "logo.txt", "image/png", "not an image".getBytes()))
                        .with(csrf()).param("name", "My Company").param("code", "MC"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Logo must be a PNG, JPEG, GIF or WebP image.")));

        mvc.perform(multipart("/settings").file(new MockMultipartFile("logoFile", "logo.png", "image/png", png))
                        .with(csrf()).param("name", "My Company").param("code", "MC"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        // Saving other details without a new file keeps the logo.
        mvc.perform(multipart("/settings").with(csrf()).param("name", "My Company").param("code", "MC"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo")).andExpect(status().isOk());

        mvc.perform(multipart("/settings").with(csrf()).param("name", "My Company").param("code", "MC").param("removeLogo", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo")).andExpect(status().isNotFound());
    }

    @Test
    @WithUserDetails("admin")
    void invalidSaleRedisplaysFormWithError() throws Exception {
        mvc.perform(post("/sales").with(csrf()).param("paid", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please select a customer.")));
    }
}
