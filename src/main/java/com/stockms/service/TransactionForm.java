package com.stockms.service;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Form backing both the purchase and the sale entry screens. */
public class TransactionForm {

    /** Supplier id for purchases, customer id for sales. */
    private Long partyId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate date = LocalDate.now();

    private List<Line> lines = new ArrayList<>();

    private BigDecimal paid = BigDecimal.ZERO;
    private String mode = "Cash";
    private String description;

    public static class Line {
        private Long productId;
        private Integer quantity;
        private BigDecimal price;

        public Line() {
        }

        public Line(Long productId, Integer quantity, BigDecimal price) {
            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
        }

        boolean isBlank() {
            return productId == null && quantity == null && price == null;
        }

        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
    }

    /** Lines the user actually filled in; completely empty rows are ignored. */
    public List<Line> filledLines() {
        return lines.stream().filter(l -> !l.isBlank()).toList();
    }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }
    public BigDecimal getPaid() { return paid; }
    public void setPaid(BigDecimal paid) { this.paid = paid; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
