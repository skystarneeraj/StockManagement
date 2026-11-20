package com.stockms;

import com.stockms.entity.*;
import com.stockms.repository.*;
import com.stockms.service.CompanyService;
import com.stockms.service.InventoryService;
import com.stockms.service.StockService;
import com.stockms.service.TransactionForm;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Company A (the sample company "MC") must never read or change company B's data, and a staff
 * user must never reach a branch they are not assigned to. Data is committed, as in real use.
 */
@StockMsTest
@AutoConfigureMockMvc
class TenantIsolationTests {

    @Autowired MockMvc mvc;
    @Autowired UserDetailsService userDetailsService;
    @Autowired TransactionTemplate tx;
    @Autowired CompanyService companyService;
    @Autowired InventoryService inventoryService;
    @Autowired StockService stockService;
    @Autowired CompanyRepository companyRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired CustomerRepository customerRepository;
    @Autowired SaleRepository saleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // Company A: the sample company, plus a second branch the staff user may not use.
    Company companyA;
    Branch headOfficeA;
    Branch secondBranchA;
    Sale saleInSecondBranch;
    // Company B, created by the super admin.
    Company companyB;
    Branch headOfficeB;
    Product productB;
    Customer customerB;
    Category categoryB;
    Sale saleB;

    @BeforeEach
    void createTwoCompanies() {
        companyA = companyRepository.findByCodeIgnoreCase("MC").orElseThrow();
        headOfficeA = as(Tenant.system(companyA.getId(), Set.of(), null),
                () -> branchRepository.findAllByOrderByHeadOfficeDescNameAsc().getFirst());
        secondBranchA = as(Tenant.system(companyA.getId(), Set.of(), null), () -> tx.execute(s ->
                branchRepository.findByCodeIgnoreCase("SB").orElseGet(() -> {
                    Branch b = new Branch();
                    b.setName("Second Branch");
                    b.setCode("SB");
                    return branchRepository.save(b);
                })));
        Tenant adminA = adminOf(companyA, secondBranchA, headOfficeA, secondBranchA);
        saleInSecondBranch = as(adminA, () -> tx.execute(s -> saleRepository
                .findByBranchIdInOrderByDateDescIdDesc(List.of(secondBranchA.getId())).stream().findFirst().orElseGet(() -> {
                    Product mouse = productRepository.findByCodeIgnoreCase("P001").orElseThrow();
                    stockService.add(secondBranchA, mouse, 10);
                    return inventoryService.createSale(saleForm(customerRepository.findAll().getFirst(), mouse), "admin");
                })));
        tx.executeWithoutResult(s -> {
            if (userRepository.findByUsernameIgnoreCase("staff_a").isEmpty()) {
                AppUser staff = new AppUser("staff_a", passwordEncoder.encode("pw"), AppUser.Role.STAFF);
                staff.setCompany(companyA);
                staff.getBranches().add(branchRepository.getReferenceById(headOfficeA.getId()));
                userRepository.save(staff);
            }
        });

        companyB = companyRepository.findByCodeIgnoreCase("TB").orElseGet(() -> tx.execute(s -> {
            Company form = new Company();
            form.setName("Other Traders");
            form.setCode("TB");
            return companyService.create(form, "admin_b", "pw");
        }));
        headOfficeB = as(Tenant.system(companyB.getId(), Set.of(), null),
                () -> branchRepository.findAllByOrderByHeadOfficeDescNameAsc().getFirst());
        Tenant adminB = adminOf(companyB, headOfficeB, headOfficeB);
        as(adminB, () -> tx.execute(s -> {
            if (productRepository.findByCodeIgnoreCase("B001").isPresent()) return null;
            Category category = categoryRepository.save(new Category("B Secret Category", null));
            Customer customer = customerRepository.save(new Customer("B Secret Customer", "", ""));
            Product product = new Product();
            product.setCode("B001");
            product.setName("B Secret Product");
            product.setCategory(category);
            productRepository.save(product);
            stockService.add(headOfficeB, product, 50);
            inventoryService.createSale(saleForm(customer, product), "admin_b");
            return null;
        }));
        as(adminB, () -> {
            productB = productRepository.findByCodeIgnoreCase("B001").orElseThrow();
            categoryB = categoryRepository.findByNameIgnoreCase("B Secret Category").orElseThrow();
            customerB = customerRepository.findAllByOrderByNameAsc().getFirst();
            saleB = saleRepository.findByBranchIdInOrderByDateDescIdDesc(List.of(headOfficeB.getId())).getFirst();
            return null;
        });
    }

    // ---- Repository level: the Hibernate filters ------------------------------------------

    @Test
    void queriesAndLoadsByIdSeeOnlyTheCurrentCompany() {
        Tenant adminA = adminOf(companyA, headOfficeA, headOfficeA, secondBranchA);
        as(adminA, () -> tx.execute(s -> {
            assertThat(productRepository.findAll()).extracting(Product::getCode).doesNotContain("B001").contains("P001");
            assertThat(productRepository.findById(productB.getId())).isEmpty();
            assertThat(customerRepository.findById(customerB.getId())).isEmpty();
            assertThat(categoryRepository.findById(categoryB.getId())).isEmpty();
            assertThat(saleRepository.findById(saleB.getId())).isEmpty();
            assertThat(branchRepository.findById(headOfficeB.getId())).isEmpty();
            assertThat(productRepository.existsById(productB.getId())).isFalse();
            return null;
        }));
        // And the other way round.
        as(adminOf(companyB, headOfficeB, headOfficeB), () -> tx.execute(s -> {
            assertThat(productRepository.findAll()).extracting(Product::getCode).containsExactly("B001");
            assertThat(saleRepository.findById(saleInSecondBranch.getId())).isEmpty();
            return null;
        }));
    }

    @Test
    void withoutACompanyNothingIsVisible() {
        tx.executeWithoutResult(s -> {
            assertThat(productRepository.findAll()).isEmpty();
            assertThat(saleRepository.findAll()).isEmpty();
            assertThat(productRepository.findById(productB.getId())).isEmpty();
        });
    }

    @Test
    void branchFilterHidesRecordsOfUnassignedBranches() {
        Tenant staffInHeadOffice = new Tenant(null, "staff_a", companyA.getId(), false, Set.of(headOfficeA.getId()), headOfficeA.getId());
        as(staffInHeadOffice, () -> tx.execute(s -> {
            assertThat(saleRepository.findById(saleInSecondBranch.getId())).isEmpty();
            assertThat(saleRepository.findAll()).allMatch(sale -> sale.getBranch().getId().equals(headOfficeA.getId()));
            return null;
        }));
    }

    // ---- Web level: company admin of A against company B -----------------------------------

    @Test
    void companyAdminCannotOpenAnotherCompanysRecords() throws Exception {
        UserDetails adminA = userDetailsService.loadUserByUsername("admin");
        mvc.perform(get("/products/{id}/edit", productB.getId()).with(user(adminA))).andExpect(status().isNotFound());
        mvc.perform(get("/customers/{id}/edit", customerB.getId()).with(user(adminA))).andExpect(status().isNotFound());
        mvc.perform(get("/categories/{id}/edit", categoryB.getId()).with(user(adminA))).andExpect(status().isNotFound());
        mvc.perform(get("/sales/{id}", saleB.getId()).with(user(adminA))).andExpect(status().isNotFound());
        mvc.perform(get("/branches/{id}/edit", headOfficeB.getId()).with(user(adminA))).andExpect(status().isNotFound());
        Long userB = userRepository.findByUsernameIgnoreCase("admin_b").orElseThrow().getId();
        mvc.perform(get("/users/{id}/edit", userB).with(user(adminA))).andExpect(status().isNotFound());

        mvc.perform(get("/products").with(user(adminA)))
                .andExpect(content().string(not(containsString("B Secret Product"))));
        mvc.perform(get("/customers").with(user(adminA)))
                .andExpect(content().string(not(containsString("B Secret Customer"))));
        mvc.perform(get("/reports/sales?branch=all&from=2000-01-01&to=2100-01-01").with(user(adminA)))
                .andExpect(content().string(not(containsString(saleB.getBillNumber()))));
    }

    @Test
    void companyAdminCannotChangeOrDeleteAnotherCompanysRecords() throws Exception {
        UserDetails adminA = userDetailsService.loadUserByUsername("admin");
        mvc.perform(post("/customers").with(user(adminA)).with(csrf())
                        .param("id", customerB.getId().toString()).param("name", "Hacked"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/categories").with(user(adminA)).with(csrf())
                        .param("id", categoryB.getId().toString()).param("name", "Hacked"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/products/{id}/delete", productB.getId()).with(user(adminA)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/sales/{id}/delete", saleB.getId()).with(user(adminA)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        as(adminOf(companyB, headOfficeB, headOfficeB), () -> tx.execute(s -> {
            assertThat(customerRepository.findById(customerB.getId())).get().extracting(Customer::getName).isEqualTo("B Secret Customer");
            assertThat(categoryRepository.findById(categoryB.getId())).get().extracting(Category::getName).isEqualTo("B Secret Category");
            assertThat(productRepository.findById(productB.getId())).isPresent();
            assertThat(saleRepository.findById(saleB.getId())).isPresent();
            return null;
        }));
    }

    @Test
    void anotherCompanysIdsCannotBeUsedInForms() throws Exception {
        UserDetails adminA = userDetailsService.loadUserByUsername("admin");
        Long mouseA = as(adminOf(companyA, headOfficeA, headOfficeA),
                () -> productRepository.findByCodeIgnoreCase("P001").orElseThrow().getId());

        mvc.perform(post("/sales").with(user(adminA)).with(csrf())
                        .param("partyId", customerB.getId().toString())
                        .param("lines[0].productId", mouseA.toString()).param("lines[0].quantity", "1").param("lines[0].price", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selected customer no longer exists.")));

        Long customerA = as(adminOf(companyA, headOfficeA, headOfficeA), () -> customerRepository.findAll().getFirst().getId());
        mvc.perform(post("/sales").with(user(adminA)).with(csrf())
                        .param("partyId", customerA.toString())
                        .param("lines[0].productId", productB.getId().toString()).param("lines[0].quantity", "1").param("lines[0].price", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Selected product no longer exists.")));
    }

    @Test
    void companyAndBranchSentByTheBrowserAreIgnored() throws Exception {
        UserDetails adminA = userDetailsService.loadUserByUsername("admin");
        String name = "Injected " + UUID.randomUUID();
        mvc.perform(post("/categories").with(user(adminA)).with(csrf())
                        .param("name", name).param("companyId", companyB.getId().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(as(adminOf(companyA, headOfficeA, headOfficeA), () -> categoryRepository.findByNameIgnoreCase(name))).isPresent();
        assertThat(as(adminOf(companyB, headOfficeB, headOfficeB), () -> categoryRepository.findByNameIgnoreCase(name))).isEmpty();
    }

    @Test
    void usersOfADeactivatedCompanyAreLockedOut() throws Exception {
        UserDetails adminB = userDetailsService.loadUserByUsername("admin_b");
        mvc.perform(get("/").with(user(adminB))).andExpect(status().isOk());
        tx.executeWithoutResult(s -> companyService.setActive(companyB.getId(), false));
        try {
            mvc.perform(get("/").with(user(adminB))).andExpect(redirectedUrl("/login?disabled"));
            mvc.perform(formLogin().user("admin_b").password("pw")).andExpect(redirectedUrl("/login?error"));
        } finally {
            tx.executeWithoutResult(s -> companyService.setActive(companyB.getId(), true));
        }
    }

    // ---- Web level: staff user against an unassigned branch --------------------------------

    @Test
    void staffCannotAccessAnUnassignedBranch() throws Exception {
        UserDetails staff = userDetailsService.loadUserByUsername("staff_a");
        mvc.perform(get("/sales/{id}", saleInSecondBranch.getId()).with(user(staff))).andExpect(status().isNotFound());
        mvc.perform(post("/sales/{id}/delete", saleInSecondBranch.getId()).with(user(staff)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/branch/switch").with(user(staff)).with(csrf()).param("branchId", secondBranchA.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/branch/switch").with(user(staff)).with(csrf()).param("branchId", "0"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reports/sales").with(user(staff)).param("branch", secondBranchA.getId().toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/sales?branch=all&from=2000-01-01&to=2100-01-01").with(user(staff)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(saleInSecondBranch.getBillNumber()))));
        mvc.perform(get("/").with(user(staff)))
                .andExpect(content().string(not(containsString("Second Branch"))))
                .andExpect(content().string(not(containsString("All branches"))));

        // Admin screens are for company admins only.
        mvc.perform(get("/branches").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/users").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(get("/settings").with(user(staff))).andExpect(status().isForbidden());

        as(adminOf(companyA, secondBranchA, headOfficeA, secondBranchA),
                () -> assertThat(saleRepository.findById(saleInSecondBranch.getId())).isPresent());
    }

    // ---- Invoice numbers under concurrent saves ----------------------------------------

    @Test
    void concurrentSalesInOneBranchGetDistinctConsecutiveNumbers() throws Exception {
        Tenant adminB = adminOf(companyB, headOfficeB, headOfficeB);
        int count = 8;
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return TenantContext.callAs(adminB, () -> {
                    TransactionForm form = saleForm(customerB, productB);
                    form.setDate(LocalDate.of(2040, 6, 1));
                    return inventoryService.createSale(form, "admin_b").getBillNumber();
                });
            }));
        }
        start.countDown();
        Set<String> numbers = new TreeSet<>();
        for (Future<String> f : results) numbers.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(numbers).hasSize(count);
        assertThat(numbers).allMatch(n -> n.startsWith("TB-HO-2040-41/"));
        List<Integer> sequence = numbers.stream().map(n -> Integer.parseInt(n.substring(n.indexOf('/') + 1))).sorted().toList();
        assertThat(sequence.getLast() - sequence.getFirst()).isEqualTo(count - 1);
    }

    // ---- helpers --------------------------------------------------------------------

    private static Tenant adminOf(Company company, Branch current, Branch... allowed) {
        return com.stockms.tenant.TestTenants.admin(company, current, allowed);
    }

    private static <T> T as(Tenant tenant, Supplier<T> work) {
        return TenantContext.callAs(tenant, work);
    }

    private static TransactionForm saleForm(Customer customer, Product product) {
        TransactionForm form = new TransactionForm();
        form.setPartyId(customer.getId());
        form.setPaid(BigDecimal.ZERO);
        form.getLines().add(new TransactionForm.Line(product.getId(), 1, new BigDecimal("10")));
        return form;
    }
}
