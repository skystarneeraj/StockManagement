package com.stockms.web;

import com.stockms.entity.Branch;
import com.stockms.repository.BranchRepository;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Branches of the signed-in admin's company (admins only, see SecurityConfig). */
@Controller
@RequestMapping("/branches")
public class BranchController {

    private final BranchRepository repository;

    public BranchController(BranchRepository repository) {
        this.repository = repository;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("states", Gst.stateNames());
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("branches", repository.findAllByOrderByHeadOfficeDescNameAsc());
        return "branches/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("branch", new Branch());
        return "branches/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("branch", repository.findById(id).orElseThrow(NotFoundException::new));
        return "branches/form";
    }

    @PostMapping
    @Transactional
    String save(@Valid @ModelAttribute("branch") Branch branch, BindingResult result, RedirectAttributes redirect) {
        if (branch.getCode() != null) {
            repository.findByCodeIgnoreCase(branch.getCode())
                    .filter(existing -> !existing.getId().equals(branch.getId()))
                    .ifPresent(existing -> result.rejectValue("code", "duplicate", "is already used by " + existing.getName()));
        }
        Branch target = branch.getId() == null ? new Branch()
                : repository.findById(branch.getId()).orElseThrow(NotFoundException::new);
        if (target.isHeadOffice() && !branch.isActive()) {
            result.rejectValue("active", "headOffice", "The head office cannot be deactivated.");
        }
        if (result.hasErrors()) return "branches/form";

        target.setName(branch.getName().trim());
        target.setCode(branch.getCode());
        target.setAddress(branch.getAddress());
        target.setPhone(branch.getPhone());
        target.setEmail(branch.getEmail());
        target.setGstin(branch.getGstin());
        target.setState(branch.getState() != null ? branch.getState() : Gst.stateFromGstin(branch.getGstin()));
        target.setActive(branch.isActive());
        if (branch.isHeadOffice() && !target.isHeadOffice()) {
            // Only one head office per company.
            repository.findAll().forEach(b -> b.setHeadOffice(false));
            target.setHeadOffice(true);
        }
        repository.save(target);
        redirect.addFlashAttribute("success", "Branch '" + target.getName() + "' saved.");
        return "redirect:/branches";
    }

    @PostMapping("/{id}/active")
    @Transactional
    String setActive(@PathVariable Long id, @RequestParam boolean active, RedirectAttributes redirect) {
        Branch branch = repository.findById(id).orElseThrow(NotFoundException::new);
        if (branch.isHeadOffice() && !active) {
            redirect.addFlashAttribute("error", "The head office cannot be deactivated.");
        } else {
            branch.setActive(active);
            redirect.addFlashAttribute("success", "Branch '" + branch.getName() + "' " + (active ? "activated." : "deactivated."));
        }
        return "redirect:/branches";
    }
}
