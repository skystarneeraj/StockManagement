package com.stockms.web;

import com.stockms.entity.Customer;
import com.stockms.repository.CustomerRepository;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("section", "customers");
        model.addAttribute("label", "Customer");
        model.addAttribute("labelPlural", "Customers");
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
        model.addAttribute("item", new Customer());
        return "party/form";
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("item", repository.findById(id).orElseThrow(NotFoundException::new));
        return "party/form";
    }

    @PostMapping
    String save(@Valid @ModelAttribute("item") Customer item, BindingResult result, RedirectAttributes redirect) {
        if (result.hasErrors()) return "party/form";

        // The balance is maintained by transactions, so only copy the editable fields.
        Customer target = item.getId() == null ? new Customer()
                : repository.findById(item.getId()).orElseThrow(NotFoundException::new);
        target.setName(item.getName());
        target.setAddress(item.getAddress());
        target.setPhone(item.getPhone());
        target.setEmail(item.getEmail());
        target.setGstin(item.getGstin());
        target.setState(item.getState() != null ? item.getState() : Gst.stateFromGstin(item.getGstin()));
        repository.save(target);
        redirect.addFlashAttribute("success", "Customer '" + target.getName() + "' saved.");
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            repository.deleteById(id);
            redirect.addFlashAttribute("success", "Customer deleted.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "This customer has sales or payments and cannot be deleted.");
        }
        return "redirect:/customers";
    }
}
