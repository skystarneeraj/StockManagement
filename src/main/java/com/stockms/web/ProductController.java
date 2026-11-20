package com.stockms.web;

import com.stockms.entity.Branch;
import com.stockms.entity.Product;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.CategoryRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.SupplierRepository;
import com.stockms.repository.UomRepository;
import com.stockms.service.Gst;
import com.stockms.service.StockService;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final SupplierRepository supplierRepository;
    private final BranchRepository branchRepository;
    private final StockService stockService;

    public ProductController(ProductRepository productRepository, CategoryRepository categoryRepository,
                             UomRepository uomRepository, SupplierRepository supplierRepository,
                             BranchRepository branchRepository, StockService stockService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.supplierRepository = supplierRepository;
        this.branchRepository = branchRepository;
        this.stockService = stockService;
    }

    @GetMapping
    String list(@RequestParam(required = false) String q,
                @RequestParam(defaultValue = "false") boolean low,
                Model model) {
        // Stock shown is for the branch selected in the top bar (or all branches).
        List<Product> products = stockService.fillQuantities(q != null && !q.isBlank()
                ? productRepository.search(q.trim()) : productRepository.findAllByOrderByNameAsc());
        model.addAttribute("products", low ? products.stream().filter(Product::isLowStock).toList() : products);
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
        Product product = productRepository.findById(id).orElseThrow(NotFoundException::new);
        stockService.fillQuantities(List.of(product));
        model.addAttribute("product", product);
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
        Tenant tenant = TenantContext.require();
        if (product.getId() == null && product.getQuantity() > 0 && tenant.branchId() == null) {
            result.rejectValue("quantity", "branch", "Choose a branch in the top bar to add opening stock.");
        }
        if (result.hasErrors()) return form(model);

        // Stock on hand changes only through purchases, sales and transfers after creation.
        Product target = product.getId() == null ? new Product()
                : productRepository.findById(product.getId()).orElseThrow(NotFoundException::new);
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
        if (product.getId() == null && product.getQuantity() > 0) {
            Branch branch = branchRepository.findById(tenant.branchId()).orElseThrow(NotFoundException::new);
            stockService.add(branch, target, product.getQuantity());
        }

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
