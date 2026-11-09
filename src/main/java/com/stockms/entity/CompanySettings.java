package com.stockms.entity;

import com.stockms.service.Gst;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Your own business details, printed on invoices. There is a single row with id 1. */
@Entity
@Table(name = "company_settings")
public class CompanySettings {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    @NotBlank
    @Size(max = 150)
    @Column(nullable = false, length = 150)
    private String name = "My Company";

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

    /** Decides CGST+SGST (same state as the party) or IGST (different state). */
    @Size(max = 50)
    @Column(length = 50)
    private String state;

    /** Optional logo printed on invoices. Kept in the database so it survives redeploys. */
    private byte[] logo;

    /** Media type of the logo, e.g. image/png. */
    @Column(length = 20)
    private String logoType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
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
}
