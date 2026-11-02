package com.stockms.web;

import com.stockms.entity.Supplier;
import com.stockms.repository.SupplierRepository;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierRepository repository;

    public SupplierController(SupplierRepository repository) {
        this.repository = repository;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("section", "suppliers");
        model.addAttribute("label", "Supplier");
        model.addAttribute("labelPlural", "Suppliers");
        model.addAttribute("states", Gst.stateNames());
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("items", repository.findAllByOrderByNameAsc());
        model.addAttribute("totalBalance", repository.totalBalance());
        return "party/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("item", new Supplier());
        return "party/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("item", repository.findById(id).orElseThrow(NotFoundException::new));
        return "party/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("item") Supplier item, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "party/form";

        // The balance is maintained by transactions, so only copy the editable fields.
        Supplier target = item.getId() == null ? new Supplier()
                : repository.findById(item.getId()).orElseThrow(NotFoundException::new);
        target.setName(item.getName());
        target.setAddress(item.getAddress());
        target.setPhone(item.getPhone());
        target.setEmail(item.getEmail());
        target.setGstin(item.getGstin());
        target.setState(item.getState() != null ? item.getState() : Gst.stateFromGstin(item.getGstin()));
        repository.save(target);
        redirect.addFlashAttribute("success", "Supplier '" + target.getName() + "' saved.");
        return "redirect:/suppliers";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repository.deleteById(id);
            redirect.addFlashAttribute("success", "Supplier deleted.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "This supplier has purchases, payments or products and cannot be deleted.");
        }
        return "redirect:/suppliers";
    }
}
