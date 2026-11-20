package com.stockms.service;

import com.stockms.entity.Branch;
import com.stockms.entity.Product;
import com.stockms.repository.BranchStockRepository;
import com.stockms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** The only place branch stock changes. Each change is one atomic UPDATE, safe under concurrent saves. */
@Service
@Transactional
public class StockService {

    private final BranchStockRepository repository;
    private final CompanyService companyService;

    public StockService(BranchStockRepository repository, CompanyService companyService) {
        this.repository = repository;
        this.companyService = companyService;
    }

    public void add(Branch branch, Product product, int quantity) {
        repository.ensureRow(branch.getId(), product.getId());
        repository.change(branch.getId(), product.getId(), quantity, true);
    }

    /**
     * Takes stock out of a branch, refusing to go below zero unless the company allows negative stock.
     *
     * @return false (and changes nothing) when there is not enough stock
     */
    public boolean tryRemove(Branch branch, Product product, int quantity) {
        repository.ensureRow(branch.getId(), product.getId());
        boolean allowNegative = companyService.current().isAllowNegativeStock();
        return repository.change(branch.getId(), product.getId(), -quantity, allowNegative) == 1;
    }

    /** Like {@link #tryRemove} but fails with a message naming the product and what is available. */
    public void remove(Branch branch, Product product, int quantity) {
        if (!tryRemove(branch, product, quantity)) {
            throw new BusinessException("Not enough stock for %s in %s: %d available, %d requested."
                    .formatted(product.getName(), branch.getName(), quantity(branch, product), quantity));
        }
    }

    @Transactional(readOnly = true)
    public int quantity(Branch branch, Product product) {
        return repository.quantity(branch.getId(), product.getId());
    }

    /** Product id to total quantity over the given branches. */
    @Transactional(readOnly = true)
    public Map<Long, Integer> totals(Collection<Long> branchIds) {
        Map<Long, Integer> totals = new HashMap<>();
        for (Object[] row : repository.totalsByProduct(branchIds)) {
            totals.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return totals;
    }

    /** Branch id to (product id to quantity). */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, Integer>> byBranch(Collection<Long> branchIds) {
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (Object[] row : repository.byBranchAndProduct(branchIds)) {
            result.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((Long) row[1], ((Number) row[2]).intValue());
        }
        return result;
    }

    /** Sets each product's displayed quantity to its stock in the branches being viewed. */
    @Transactional(readOnly = true)
    public <T extends Collection<Product>> T fillQuantities(T products) {
        Map<Long, Integer> totals = totals(TenantContext.require().scopeForQuery());
        products.forEach(p -> p.setQuantity(totals.getOrDefault(p.getId(), 0)));
        return products;
    }
}
