package com.stockms.web;

import com.stockms.entity.Sale;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.SaleRepository;
import com.stockms.service.BusinessException;
import com.stockms.service.InventoryService;
import com.stockms.service.SettingsService;
import com.stockms.service.TransactionForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/sales")
public class SaleController {

    private final SaleRepository saleRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final SettingsService settingsService;

    public SaleController(SaleRepository saleRepository, CustomerRepository customerRepository,
                          ProductRepository productRepository, InventoryService inventoryService,
                          SettingsService settingsService) {
        this.saleRepository = saleRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.settingsService = settingsService;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("isSale", true);
        model.addAttribute("section", "sales");
        model.addAttribute("title", "Sales");
        model.addAttribute("partyLabel", "Customer");
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("txns", saleRepository.findAllByOrderByDateDescIdDesc());
        return "transactions/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        TransactionForm form = new TransactionForm();
        form.getLines().add(new TransactionForm.Line());
        model.addAttribute("form", form);
        return form(model);
    }

    @PostMapping
    String save(@ModelAttribute("form") TransactionForm form, Principal principal,
                Model model, RedirectAttributes redirect) {
        try {
            Sale sale = inventoryService.createSale(form, principal.getName());
            redirect.addFlashAttribute("success", "Invoice " + sale.getBillNumber() + " saved and stock updated.");
            return "redirect:/sales/" + sale.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (form.getLines().isEmpty()) form.getLines().add(new TransactionForm.Line());
            return form(model);
        }
    }

    @GetMapping("/{id}")
    String view(@PathVariable Long id, Model model) {
        model.addAttribute("company", settingsService.get());
        model.addAttribute("txn", saleRepository.findById(id).orElseThrow(NotFoundException::new));
        return "transactions/view";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            inventoryService.deleteSale(id);
            redirect.addFlashAttribute("success", "Sale deleted and stock returned.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sales";
    }

    private String form(Model model) {
        model.addAttribute("parties", customerRepository.findAllByOrderByNameAsc());
        model.addAttribute("products", productRepository.findAllByOrderByNameAsc());
        return "transactions/form";
    }
}
