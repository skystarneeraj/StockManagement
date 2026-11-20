package com.stockms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Goods sent from one branch to another. Stock leaves the source branch when dispatched
 * and arrives in the destination branch when received.
 */
@Entity
@Table(name = "stock_transfers")
@Filter(name = BranchOwned.BRANCH_FILTER, condition = "(from_branch_id in (:branchIds) or to_branch_id in (:branchIds))")
public class StockTransfer extends CompanyOwned {

    public enum Status {
        DRAFT("Draft"), IN_TRANSIT("In transit"), RECEIVED("Received");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String transferNumber;

    @ManyToOne(optional = false)
    @JoinColumn(updatable = false)
    private Branch fromBranch;

    @ManyToOne(optional = false)
    @JoinColumn(updatable = false)
    private Branch toBranch;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(length = 255)
    private String notes;

    @Column(length = 50)
    private String createdBy;

    @Column(length = 50)
    private String receivedBy;

    private LocalDate receivedDate;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<StockTransferItem> items = new ArrayList<>();

    @Override
    protected void checkOwnership() {
        // The destination may be any branch of the company; the user must be able to send from the source.
        if (toBranch == null || !toBranch.getCompanyId().equals(getCompanyId())) {
            throw new IllegalStateException("Destination branch must belong to the same company.");
        }
        if (status == Status.DRAFT) BranchOwned.checkBranch(fromBranch, getCompanyId());
    }

    public void addItem(StockTransferItem item) {
        item.setTransfer(this);
        items.add(item);
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(StockTransferItem::getQuantity).sum();
    }

    public Long getId() { return id; }
    public String getTransferNumber() { return transferNumber; }
    public void setTransferNumber(String transferNumber) { this.transferNumber = transferNumber; }
    public Branch getFromBranch() { return fromBranch; }
    public void setFromBranch(Branch fromBranch) { this.fromBranch = fromBranch; }
    public Branch getToBranch() { return toBranch; }
    public void setToBranch(Branch toBranch) { this.toBranch = toBranch; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public List<StockTransferItem> getItems() { return items; }
}
