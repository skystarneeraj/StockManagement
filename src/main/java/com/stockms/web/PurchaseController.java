package com.stockms.web;

import com.stockms.entity.Purchase;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.PurchaseRepository;
import com.stockms.repository.SupplierRepository;
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
@RequestMapping("/purchases")
public class PurchaseController {

    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final SettingsService settingsService;

    public PurchaseController(PurchaseRepository purchaseRepository, SupplierRepository supplierRepository,
                              ProductRepository productRepository, InventoryService inventoryService,
                              SettingsService settingsService) {
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.inventoryService = inventoryService;
        this.settingsService = settingsService;
    }

    @ModelAttribute
    void labels(Model model) {
        model.addAttribute("isSale", false);
        model.addAttribute("section", "purchases");
        model.addAttribute("title", "Purchases");
        model.addAttribute("partyLabel", "Supplier");
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("txns", purchaseRepository.findAllByOrderByDateDescIdDesc());
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
            Purchase purchase = inventoryService.createPurchase(form, principal.getName());
            redirect.addFlashAttribute("success", "Purchase " + purchase.getBillNumber() + " saved and stock updated.");
            return "redirect:/purchases/" + purchase.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (form.getLines().isEmpty()) form.getLines().add(new TransactionForm.Line());
            return form(model);
        }
    }

    @GetMapping("/{id}")
    String view(@PathVariable Long id, Model model) {
        model.addAttribute("company", settingsService.get());
        model.addAttribute("txn", purchaseRepository.findById(id).orElseThrow(NotFoundException::new));
        return "transactions/view";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            inventoryService.deletePurchase(id);
            redirect.addFlashAttribute("success", "Purchase deleted and stock reversed.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/purchases";
    }

    private String form(Model model) {
        model.addAttribute("parties", supplierRepository.findAllByOrderByNameAsc());
        model.addAttribute("products", productRepository.findAllByOrderByNameAsc());
        return "transactions/form";
    }
}
