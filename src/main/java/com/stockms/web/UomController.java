package com.stockms.web;

import com.stockms.entity.Uom;
import com.stockms.repository.UomRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/uoms")
public class UomController {

    private final UomRepository repository;

    public UomController(UomRepository repository) {
        this.repository = repository;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("section", "uoms");
        model.addAttribute("label", "Unit");
        model.addAttribute("labelPlural", "Units");
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("items", repository.findAllByOrderByNameAsc());
        return "simple/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("item", new Uom());
        return "simple/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("item", repository.findById(id).orElseThrow(NotFoundException::new));
        return "simple/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("item") Uom item, BindingResult result, RedirectAttributes redirect) {
        repository.findByNameIgnoreCase(item.getName() == null ? "" : item.getName().trim())
                .filter(existing -> !existing.getId().equals(item.getId()))
                .ifPresent(existing -> result.rejectValue("name", "duplicate", "already exists"));
        if (result.hasErrors()) return "simple/form";

        // Load the stored record (not found if it is another company's) and copy the editable fields.
        Uom target = item.getId() == null ? new Uom()
                : repository.findById(item.getId()).orElseThrow(NotFoundException::new);
        target.setName(item.getName().trim());
        target.setDescription(item.getDescription());
        repository.save(target);
        redirect.addFlashAttribute("success", "Unit '" + target.getName() + "' saved.");
        return "redirect:/uoms";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repository.deleteById(id);
            redirect.addFlashAttribute("success", "Unit deleted.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "This unit is used by products and cannot be deleted.");
        }
        return "redirect:/uoms";
    }
}
