package com.stockms.entity;

import com.stockms.service.Gst;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Purchase purchase;

    @ManyToOne(optional = false)
    private Product product;

    private int quantity;

    /** Unit price before GST. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    /** HSN and rate are copied from the product so old bills don't change when the product does. */
    @Column(length = 8)
    private String hsnCode;

    @ColumnDefault("0")
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal gstRate = BigDecimal.ZERO;

    @ColumnDefault("0")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    public PurchaseItem() {
    }

    public PurchaseItem(Product product, int quantity, BigDecimal price) {
        this.product = product;
        this.quantity = quantity;
        this.price = price;
        this.hsnCode = product.getHsnCode();
        this.gstRate = product.getGstRate();
        this.taxAmount = Gst.tax(getTaxableValue(), gstRate);
    }

    public BigDecimal getTaxableValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    /** Line amount including GST. */
    public BigDecimal getTotal() {
        return getTaxableValue().add(taxAmount);
    }

    public Long getId() { return id; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase purchase) { this.purchase = purchase; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getHsnCode() { return hsnCode; }
    public BigDecimal getGstRate() { return gstRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
}
