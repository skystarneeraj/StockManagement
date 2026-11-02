package com.stockms.entity;

public enum PaymentType {
    /** Money paid by us to a supplier. */
    SUPPLIER_PAYMENT("Paid to supplier"),
    /** Money received by us from a customer. */
    CUSTOMER_RECEIPT("Received from customer");

    private final String label;

    PaymentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
