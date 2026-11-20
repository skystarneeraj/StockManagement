package com.stockms.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

/** Quantity of one product in one branch. Changed only through StockService. */
@Entity
@Table(name = "branch_stock", uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "product_id"}))
@Filter(name = BranchOwned.BRANCH_FILTER)
public class BranchStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Branch branch;

    @ManyToOne(optional = false)
    private Product product;

    private int quantity;

    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
}
