package com.stockms.service;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Form backing the stock transfer entry screen. The source is always the current branch. */
public class TransferForm {

    private Long toBranchId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date = LocalDate.now();

    private String notes;

    private List<Line> lines = new ArrayList<>();

    public static class Line {
        private Long productId;
        private Integer quantity;

        public Line() {
        }

        public Line(Long productId, Integer quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        boolean isBlank() {
            return productId == null && quantity == null;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    /** Lines the user actually filled in; completely empty rows are ignored. */
    public List<Line> filledLines() {
        return lines.stream().filter(l -> !l.isBlank()).toList();
    }

    public Long getToBranchId() { return toBranchId; }
    public void setToBranchId(Long toBranchId) { this.toBranchId = toBranchId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }
}
