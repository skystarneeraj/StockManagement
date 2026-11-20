package com.stockms.web;

import com.stockms.entity.StockTransfer;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.StockTransferRepository;
import com.stockms.service.BusinessException;
import com.stockms.service.StockService;
import com.stockms.service.StockTransferService;
import com.stockms.service.TransferForm;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.function.Function;

@Controller
@RequestMapping("/transfers")
public class StockTransferController {

    private final StockTransferRepository repository;
    private final StockTransferService transferService;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;

    public StockTransferController(StockTransferRepository repository, StockTransferService transferService,
                                   BranchRepository branchRepository, ProductRepository productRepository,
                                   StockService stockService) {
        this.repository = repository;
        this.transferService = transferService;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
    }

    /** Transfers from or to the branch selected in the top bar (or any allowed branch). */
    @GetMapping
    String list(Model model) {
        model.addAttribute("transfers", repository.findInvolving(TenantContext.require().scopeForQuery()));
        return "transfers/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        TransferForm form = new TransferForm();
        form.getLines().add(new TransferForm.Line());
        model.addAttribute("form", form);
        return form(model);
    }

    @PostMapping
    String save(@ModelAttribute("form") TransferForm form, Principal principal,
                Model model, RedirectAttributes redirect) {
        try {
            StockTransfer transfer = transferService.create(form, principal.getName());
            redirect.addFlashAttribute("success", "Transfer " + transfer.getTransferNumber()
                    + " saved as draft. Dispatch it to take the stock out of " + transfer.getFromBranch().getName() + ".");
            return "redirect:/transfers/" + transfer.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            if (form.getLines().isEmpty()) form.getLines().add(new TransferForm.Line());
            return form(model);
        }
    }

    @GetMapping("/{id}")
    String view(@PathVariable Long id, Model model) {
        // Not found unless it is from or to a branch the user may access.
        StockTransfer transfer = repository.findById(id).orElseThrow(NotFoundException::new);
        Tenant tenant = TenantContext.require();
        model.addAttribute("transfer", transfer);
        model.addAttribute("canSend", tenant.canAccess(transfer.getFromBranch().getId()));
        model.addAttribute("canReceive", tenant.canAccess(transfer.getToBranch().getId()));
        return "transfers/view";
    }

    @PostMapping("/{id}/dispatch")
    String dispatch(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, redirect, transferService::dispatch, "dispatched; stock taken out of %s.", true);
    }

    @PostMapping("/{id}/receive")
    String receive(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
        return act(id, redirect, tid -> transferService.receive(tid, principal.getName()), "received; stock added to %s.", false);
    }

    @PostMapping("/{id}/recall")
    String recall(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, redirect, transferService::recall, "recalled; stock returned to %s.", true);
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            transferService.delete(id);
            redirect.addFlashAttribute("success", "Draft transfer deleted.");
            return "redirect:/transfers";
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/transfers/" + id;
        }
    }

    private String act(Long id, RedirectAttributes redirect, Function<Long, StockTransfer> action,
                       String message, boolean sourceBranch) {
        try {
            StockTransfer t = action.apply(id);
            redirect.addFlashAttribute("success", "Transfer " + t.getTransferNumber() + " "
                    + message.formatted((sourceBranch ? t.getFromBranch() : t.getToBranch()).getName()));
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/transfers/" + id;
    }

    private String form(Model model) {
        Long current = TenantContext.require().branchId();
        model.addAttribute("toBranches", branchRepository.findByActiveTrueOrderByHeadOfficeDescNameAsc().stream()
                .filter(b -> !b.getId().equals(current)).toList());
        model.addAttribute("products", stockService.fillQuantities(productRepository.findAllByOrderByNameAsc()));
        return "transfers/form";
    }
}
