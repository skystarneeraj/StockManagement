package com.stockms.web;

import com.stockms.entity.AppUser;
import com.stockms.repository.AppUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/** User management; restricted to admins in SecurityConfig. */
@Controller
@RequestMapping("/users")
public class UserController {

    public static class UserForm {
        private Long id;

        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "letters, digits, dot, dash or underscore only")
        private String username;

        /** Required for new users; blank on edit keeps the current password. */
        @Size(max = 100)
        private String password;

        @NotNull
        private AppUser.Role role = AppUser.Role.STAFF;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public AppUser.Role getRole() { return role; }
        public void setRole(AppUser.Role role) { this.role = role; }
    }

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("users", userRepository.findAllByOrderByUsernameAsc());
        return "users/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("user", new UserForm());
        return "users/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        AppUser user = userRepository.findById(id).orElseThrow(NotFoundException::new);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setRole(user.getRole());
        model.addAttribute("user", form);
        return "users/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("user") UserForm form, BindingResult result,
                Principal principal, RedirectAttributes redirect) {
        boolean isNew = form.getId() == null;
        String password = form.getPassword() == null ? "" : form.getPassword();
        if (isNew && password.length() < 4) {
            result.rejectValue("password", "required", "must be at least 4 characters");
        } else if (!isNew && !password.isEmpty() && password.length() < 4) {
            result.rejectValue("password", "short", "must be at least 4 characters");
        }
        if (form.getUsername() != null) {
            userRepository.findByUsernameIgnoreCase(form.getUsername())
                    .filter(existing -> !existing.getId().equals(form.getId()))
                    .ifPresent(existing -> result.rejectValue("username", "duplicate", "is already taken"));
        }
        if (result.hasErrors()) return "users/form";

        AppUser user = isNew ? new AppUser() : userRepository.findById(form.getId()).orElseThrow(NotFoundException::new);
        if (!isNew && user.getUsername().equalsIgnoreCase(principal.getName()) && form.getRole() != AppUser.Role.ADMIN) {
            redirect.addFlashAttribute("error", "You cannot remove your own admin role.");
            return "redirect:/users";
        }
        user.setUsername(form.getUsername());
        user.setRole(form.getRole());
        if (!password.isEmpty()) user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        redirect.addFlashAttribute("success", "User '" + user.getUsername() + "' saved.");
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        AppUser user = userRepository.findById(id).orElseThrow(NotFoundException::new);
        if (user.getUsername().equalsIgnoreCase(principal.getName())) {
            redirect.addFlashAttribute("error", "You cannot delete your own account.");
        } else {
            userRepository.delete(user);
            redirect.addFlashAttribute("success", "User '" + user.getUsername() + "' deleted.");
        }
        return "redirect:/users";
    }
}
