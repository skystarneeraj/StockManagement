package com.stockms.service;

import com.stockms.entity.Branch;
import com.stockms.entity.Product;
import com.stockms.entity.StockTransfer;
import com.stockms.entity.StockTransfer.Status;
import com.stockms.entity.StockTransferItem;
import com.stockms.repository.BranchRepository;
import com.stockms.repository.ProductRepository;
import com.stockms.repository.StockTransferRepository;
import com.stockms.service.DocNumberService.DocType;
import com.stockms.tenant.Tenant;
import com.stockms.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Moves stock between branches of a company: a DRAFT is dispatched (stock leaves the source,
 * IN_TRANSIT), then received (stock enters the destination, RECEIVED). A transfer in transit can
 * be recalled, which returns the stock to the source and makes it a draft again.
 */
@Service
@Transactional
public class StockTransferService {

    private final StockTransferRepository repository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final StockService stockService;
    private final DocNumberService docNumberService;
    private final CompanyService companyService;

    public StockTransferService(StockTransferRepository repository, BranchRepository branchRepository,
                                ProductRepository productRepository, StockService stockService,
                                DocNumberService docNumberService, CompanyService companyService) {
        this.repository = repository;
        this.branchRepository = branchRepository;
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.docNumberService = docNumberService;
        this.companyService = companyService;
    }

    /** Creates a draft transfer from the current branch. */
    public StockTransfer create(TransferForm form, String username) {
        Branch from = activeBranch(TenantContext.require().requireBranchId());
        if (form.getToBranchId() == null) throw new BusinessException("Please select the destination branch.");
        Branch to = activeBranch(form.getToBranchId());
        if (to.getId().equals(from.getId())) throw new BusinessException("Destination must be a different branch.");

        StockTransfer transfer = new StockTransfer();
        transfer.setFromBranch(from);
        transfer.setToBranch(to);
        transfer.setDate(form.getDate() != null ? form.getDate() : LocalDate.now());
        transfer.setNotes(form.getNotes());
        transfer.setCreatedBy(username);
        for (TransferForm.Line line : checkedLines(form)) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new BusinessException("Selected product no longer exists."));
            transfer.addItem(new StockTransferItem(product, line.getQuantity()));
        }
        transfer.setTransferNumber(docNumberService.next(companyService.current(), from, DocType.TRANSFER, transfer.getDate()));
        return repository.save(transfer);
    }

    public StockTransfer dispatch(Long id) {
        StockTransfer transfer = find(id);
        requireStatus(transfer, Status.DRAFT, "dispatched");
        requireAccess(transfer.getFromBranch());
        for (StockTransferItem item : transfer.getItems()) {
            stockService.remove(transfer.getFromBranch(), item.getProduct(), item.getQuantity());
        }
        transfer.setStatus(Status.IN_TRANSIT);
        return transfer;
    }

    public StockTransfer receive(Long id, String username) {
        StockTransfer transfer = find(id);
        requireStatus(transfer, Status.IN_TRANSIT, "received");
        requireAccess(transfer.getToBranch());
        for (StockTransferItem item : transfer.getItems()) {
            stockService.add(transfer.getToBranch(), item.getProduct(), item.getQuantity());
        }
        transfer.setStatus(Status.RECEIVED);
        transfer.setReceivedBy(username);
        transfer.setReceivedDate(LocalDate.now());
        return transfer;
    }

    /** Takes back a transfer that has not arrived yet: its stock returns to the source branch. */
    public StockTransfer recall(Long id) {
        StockTransfer transfer = find(id);
        requireStatus(transfer, Status.IN_TRANSIT, "recalled");
        requireAccess(transfer.getFromBranch());
        for (StockTransferItem item : transfer.getItems()) {
            stockService.add(transfer.getFromBranch(), item.getProduct(), item.getQuantity());
        }
        transfer.setStatus(Status.DRAFT);
        return transfer;
    }

    public void delete(Long id) {
        StockTransfer transfer = find(id);
        requireStatus(transfer, Status.DRAFT, "deleted");
        requireAccess(transfer.getFromBranch());
        repository.delete(transfer);
    }

    private StockTransfer find(Long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException("Transfer not found."));
    }

    private Branch activeBranch(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Selected branch no longer exists."));
        if (!branch.isActive()) throw new BusinessException("Branch " + branch.getName() + " is inactive.");
        return branch;
    }

    private static void requireStatus(StockTransfer transfer, Status status, String action) {
        if (transfer.getStatus() != status) {
            throw new BusinessException("Only a transfer that is %s can be %s."
                    .formatted(status.getLabel().toLowerCase(), action));
        }
    }

    /** Seeing a transfer needs access to either branch; acting on it needs access to the branch that acts. */
    private static void requireAccess(Branch branch) {
        Tenant tenant = TenantContext.require();
        if (!tenant.canAccess(branch.getId())) {
            throw new BusinessException("Only users of " + branch.getName() + " can do this.");
        }
    }

    private static List<TransferForm.Line> checkedLines(TransferForm form) {
        List<TransferForm.Line> lines = form.filledLines();
        if (lines.isEmpty()) throw new BusinessException("Add at least one item.");
        int row = 1;
        for (TransferForm.Line line : lines) {
            if (line.getProductId() == null) throw new BusinessException("Row %d: select a product.".formatted(row));
            if (line.getQuantity() == null || line.getQuantity() <= 0)
                throw new BusinessException("Row %d: quantity must be at least 1.".formatted(row));
            row++;
        }
        return lines;
    }
}
