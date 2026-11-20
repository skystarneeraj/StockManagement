package com.stockms.entity;

import com.stockms.service.Gst;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** A business using the system. Its details and logo are printed on invoices. */
@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name = "My Company";

    /** Short code at the start of invoice numbers, e.g. KT in KT-DEL-2026-27/0001. */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,10}", message = "must be 1-10 capital letters or digits")
    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 100)
    private String email;

    @Pattern(regexp = Gst.GSTIN_PATTERN, message = "is not a valid 15-character GSTIN")
    @Column(length = 15)
    private String gstin;

    /** Registered state; GST on bills is decided by each branch's own state. */
    @Size(max = 50)
    @Column(length = 50)
    private String state;

    /** Optional logo printed on invoices. Kept in the database so it survives redeploys. */
    private byte[] logo;

    /** Media type of the logo, e.g. image/png. */
    @Column(length = 20)
    private String logoType;

    /** When false, a sale or transfer may not take a branch's stock below zero. */
    @Column(nullable = false)
    private boolean allowNegativeStock;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void stampCreated() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code == null ? null : code.trim().toUpperCase(); }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin == null ? null : gstin.trim().toUpperCase(); }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public byte[] getLogo() { return logo; }
    public void setLogo(byte[] logo) { this.logo = logo; }
    public String getLogoType() { return logoType; }
    public void setLogoType(String logoType) { this.logoType = logoType; }
    public boolean hasLogo() { return logo != null && logo.length > 0; }
    public boolean isAllowNegativeStock() { return allowNegativeStock; }
    public void setAllowNegativeStock(boolean allowNegativeStock) { this.allowNegativeStock = allowNegativeStock; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
