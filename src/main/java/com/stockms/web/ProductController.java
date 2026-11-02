package com.stockms.web;

import com.stockms.entity.Product;
import com.stockms.repository.CategoryRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.repository.UomRepository;
import com.stockms.service.Gst;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final SupplierRepository supplierRepository;

    public ProductController(ProductRepository productRepository, CategoryRepository categoryRepository,
                             UomRepository uomRepository, SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    String list(@RequestParam(required = false) String q,
                @RequestParam(defaultValue = "false") boolean low,
                Model model) {
        if (low) {
            model.addAttribute("products", productRepository.findLowStock());
        } else if (q != null && !q.isBlank()) {
            model.addAttribute("products", productRepository.search(q.trim()));
        } else {
            model.addAttribute("products", productRepository.findAllByOrderByNameAsc());
        }
        model.addAttribute("q", q);
        model.addAttribute("low", low);
        return "products/list";
    }

    @GetMapping("/new")
    String create(Model model) {
        model.addAttribute("product", new Product());
        return form(model);
    }

    @GetMapping("/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        model.addAttribute("product", productRepository.findById(id).orElseThrow(NotFoundException::new));
        return form(model);
    }

    @PostMapping
    String save(@Valid @ModelAttribute("product") Product product, BindingResult result,
                Model model, RedirectAttributes redirect) {
        if (product.getCode() != null) {
            productRepository.findByCodeIgnoreCase(product.getCode().trim())
                    .filter(existing -> !existing.getId().equals(product.getId()))
                    .ifPresent(existing -> result.rejectValue("code", "duplicate", "is already used by " + existing.getName()));
        }
        if (result.hasErrors()) return form(model);

        Product target;
        if (product.getId() == null) {
            target = new Product();
            target.setQuantity(product.getQuantity()); // opening stock
        } else {
            // Stock on hand changes only through purchases and sales.
            target = productRepository.findById(product.getId()).orElseThrow(NotFoundException::new);
        }
        target.setCode(product.getCode().trim());
        target.setName(product.getName().trim());
        target.setCategory(product.getCategory());
        target.setUom(product.getUom());
        target.setSupplier(product.getSupplier());
        target.setCostPrice(product.getCostPrice());
        target.setSellingPrice(product.getSellingPrice());
        target.setReorderLevel(product.getReorderLevel());
        target.setExpiryDate(product.getExpiryDate());
        target.setHsnCode(product.getHsnCode());
        target.setGstRate(product.getGstRate());
        productRepository.save(target);

        redirect.addFlashAttribute("success", "Product '" + target.getName() + "' saved.");
        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            productRepository.deleteById(id);
            redirect.addFlashAttribute("success", "Product deleted.");
        } catch (DataAccessException e) {
            redirect.addFlashAttribute("error", "This product appears in purchases or sales and cannot be deleted.");
        }
        return "redirect:/products";
    }

    private String form(Model model) {
        model.addAttribute("categories", categoryRepository.findAllByOrderByNameAsc());
        model.addAttribute("uoms", uomRepository.findAllByOrderByNameAsc());
        model.addAttribute("suppliers", supplierRepository.findAllByOrderByNameAsc());
        model.addAttribute("gstRates", Gst.COMMON_RATES);
        return "products/form";
    }
}
