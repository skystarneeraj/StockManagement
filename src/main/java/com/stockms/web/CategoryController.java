package com.stockms.web;

import com.stockms.entity.Category;
import com.stockms.repository.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository repository;

    public CategoryController(CategoryRepository repository) {
        this.repository = repository;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("section", "categories");
        model.addAttribute("label", "Category");
        model.addAttribute("labelPlural", "Categories");
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("items", repository.findAllByOrderByNameAsc());
        return "simple/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("item", new Category());
        return "simple/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("item", repository.findById(id).orElseThrow(NotFoundException::new));
        return "simple/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("item") Category item, BindingResult result, RedirectAttributes redirect) {
        repository.findByNameIgnoreCase(item.getName() == null ? "" : item.getName().trim())
                .filter(existing -> !existing.getId().equals(item.getId()))
                .ifPresent(existing -> result.rejectValue("name", "duplicate", "already exists"));
        if (result.hasErrors()) return "simple/form";

        item.setName(item.getName().trim());
        repository.save(item);
        redirect.addFlashAttribute("success", "Category '" + item.getName() + "' saved.");
        return "redirect:/categories";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repository.deleteById(id);
            redirect.addFlashAttribute("success", "Category deleted.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "This category is used by products and cannot be deleted.");
        }
        return "redirect:/categories";
    }
}
