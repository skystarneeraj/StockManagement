package com.stockms.config;

import com.stockms.repository.AppUserRepository;
import com.stockms.tenant.TenantUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/login", "/error").permitAll()
                        .requestMatchers("/companies/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/users/**", "/settings/**", "/branches/**").hasRole("COMPANY_ADMIN")
                        // Everything else is company data; the super admin has none.
                        .anyRequest().hasAnyRole("COMPANY_ADMIN", "BRANCH_MANAGER", "STAFF"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, auth) -> {
                            boolean superAdmin = auth.getAuthorities().stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
                            response.sendRedirect(request.getContextPath() + (superAdmin ? "/companies" : "/"));
                        })
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Users of a deactivated company cannot sign in (the account is reported as disabled). */
    @Bean
    UserDetailsService userDetailsService(AppUserRepository users) {
        return username -> users.findByUsernameIgnoreCase(username)
                .map(TenantUser::new)
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }
}
