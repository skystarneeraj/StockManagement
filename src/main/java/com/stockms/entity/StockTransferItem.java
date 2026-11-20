package com.stockms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private StockTransfer transfer;

    @ManyToOne(optional = false)
    private Product product;

    private int quantity;

    public StockTransferItem() {
    }

    public StockTransferItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public StockTransfer getTransfer() { return transfer; }
    public void setTransfer(StockTransfer transfer) { this.transfer = transfer; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
