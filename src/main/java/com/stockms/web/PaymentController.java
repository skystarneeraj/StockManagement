package com.stockms.web;

import com.stockms.entity.Payment;
import com.stockms.entity.PaymentType;
import com.stockms.repository.CustomerRepository;
import com.stockms.repository.PaymentRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.service.BusinessException;
import com.stockms.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;

@Controller
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;

    public PaymentController(PaymentRepository paymentRepository, SupplierRepository supplierRepository,
                             CustomerRepository customerRepository, InventoryService inventoryService) {
        this.paymentRepository = paymentRepository;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("payments", paymentRepository.findAllByOrderByDateDescIdDesc());
        return "payments/list";
    }

    @GetMapping("/new")
    String create(@RequestParam(defaultValue = "CUSTOMER_RECEIPT") PaymentType type,
                  @RequestParam(required = false) Long party, Model model) {
        Payment payment = new Payment();
        payment.setType(type);
        if (party != null) {
            if (type == PaymentType.SUPPLIER_PAYMENT) payment.setSupplier(supplierRepository.findById(party).orElse(null));
            else payment.setCustomer(customerRepository.findById(party).orElse(null));
        }
        model.addAttribute("payment", payment);
        return form(model);
    }

    @PostMapping
    String save(@Valid @ModelAttribute("payment") Payment payment, BindingResult result,
                Principal principal, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) return form(model);
        try {
            inventoryService.recordPayment(payment, principal.getName());
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return form(model);
        }
        redirect.addFlashAttribute("success", "Payment of " + payment.getAmount() + " recorded for " + payment.getPartyName() + ".");
        return "redirect:/payments";
    }

    private String form(Model model) {
        model.addAttribute("suppliers", supplierRepository.findByBalanceGreaterThanOrderByNameAsc(BigDecimal.ZERO));
        model.addAttribute("customers", customerRepository.findByBalanceGreaterThanOrderByNameAsc(BigDecimal.ZERO));
        return "payments/form";
    }
}
