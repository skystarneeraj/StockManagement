package com.stockms.entity;

import com.stockms.service.Gst;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A shop, godown or office of a company. Stock and transactions are kept per branch. */
@Entity
@Table(name = "branches", uniqueConstraints = @UniqueConstraint(columnNames = {"company_id", "code"}))
public class Branch extends CompanyOwned {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    /** Short code in invoice numbers, e.g. DEL in KT-DEL-2026-27/0001. */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{1,10}", message = "must be 1-10 capital letters or digits")
    @Column(nullable = false, length = 10)
    private String code;

    @Size(max = 255)
    private String address;

    @Size(max = 20)
    private String phone;

    @Email
    @Size(max = 100)
    private String email;

    /** Own GSTIN when the branch is registered in another state; otherwise the company's is used. */
    @Pattern(regexp = Gst.GSTIN_PATTERN, message = "is not a valid 15-character GSTIN")
    @Column(length = 15)
    private String gstin;

    /** Decides CGST+SGST (same state as the party) or IGST (different state) on this branch's bills. */
    @Size(max = 50)
    @Column(length = 50)
    private String state;

    @Column(nullable = false)
    private boolean headOffice;

    @Column(nullable = false)
    private boolean active = true;

    /** GSTIN printed on this branch's invoices. */
    public String gstinOr(Company company) {
        return gstin != null ? gstin : company.getGstin();
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
    public boolean isHeadOffice() { return headOffice; }
    public void setHeadOffice(boolean headOffice) { this.headOffice = headOffice; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
