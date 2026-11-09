package com.stockms;

import com.stockms.repository.CustomerRepository;
import com.stockms.repository.ProductRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Renders every screen so template mistakes fail the build. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/stockdb_test",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class WebPagesTests {

    @Autowired MockMvc mvc;
    @Autowired ProductRepository productRepository;
    @Autowired CustomerRepository customerRepository;

    @Test
    void anonymousUserIsSentToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/products", "/products?low=true", "/products?q=mouse", "/products/new", "/products/1/edit",
            "/categories", "/categories/new", "/uoms", "/uoms/1/edit", "/suppliers", "/suppliers/new",
            "/customers", "/customers/1/edit", "/sales", "/sales/new", "/purchases", "/purchases/new",
            "/payments", "/payments/new", "/payments/new?type=SUPPLIER_PAYMENT",
            "/reports/sales", "/reports/purchases?from=2020-01-01&to=2030-12-31", "/reports/stock",
            "/reports/gst", "/reports/gst?from=2020-01-01&to=2030-12-31", "/settings",
            "/users", "/users/new", "/users/1/edit"})
    @WithMockUser(username = "admin", roles = "ADMIN")
    void pageRenders(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void staffCannotManageUsers() throws Exception {
        mvc.perform(get("/users").with(user("clerk").roles("STAFF"))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void saleCanBeEnteredThroughTheFormAndPrinted() throws Exception {
        Long productId = productRepository.findByCodeIgnoreCase("P001").orElseThrow().getId();
        Long customerId = customerRepository.findAll().getFirst().getId();

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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("USB Mouse")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TAX INVOICE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("8471")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidGstinIsRejectedAndValidOneFillsState() throws Exception {
        mvc.perform(post("/customers").with(csrf()).param("name", "Bad GST Co").param("gstin", "12345"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("not a valid 15-character GSTIN")));

        mvc.perform(post("/customers").with(csrf()).param("name", "Good GST Co").param("gstin", "33abcde1234f1z5")
                        .param("state", ""))
                .andExpect(status().is3xxRedirection());
        org.assertj.core.api.Assertions.assertThat(customerRepository.findAll())
                .anyMatch(c -> "33ABCDE1234F1Z5".equals(c.getGstin()) && "Tamil Nadu".equals(c.getState()));
    }

    @Test
    void staffCannotChangeCompanySettings() throws Exception {
        mvc.perform(get("/settings").with(user("clerk").roles("STAFF"))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void logoCanBeUploadedKeptAndRemoved() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0};

        mvc.perform(multipart("/settings").file(new MockMultipartFile("logoFile", "logo.txt", "image/png", "not an image".getBytes()))
                        .with(csrf()).param("name", "Logo Co"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Logo must be a PNG, JPEG, GIF or WebP image.")));

        mvc.perform(multipart("/settings").file(new MockMultipartFile("logoFile", "logo.png", "image/png", png))
                        .with(csrf()).param("name", "Logo Co"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo").with(user("clerk").roles("STAFF")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png));

        // Saving other details without a new file keeps the logo.
        mvc.perform(multipart("/settings").with(csrf()).param("name", "Logo Co Renamed"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo")).andExpect(status().isOk());

        mvc.perform(multipart("/settings").with(csrf()).param("name", "Logo Co").param("removeLogo", "true"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/company-logo")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void invalidSaleRedisplaysFormWithError() throws Exception {
        mvc.perform(post("/sales").with(csrf()).param("paid", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Please select a customer.")));
    }
}
