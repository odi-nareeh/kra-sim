package com.krasim.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "taxpayers")
public class Taxpayer {

    @Id
    @Column(name = "pin", length = 11)
    private String pin;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    protected Taxpayer() {
        // required by JPA
    }

    public Taxpayer(String pin, String companyName, String email) {
        this.pin = pin;
        this.companyName = companyName;
        this.email = email;
    }

    public String getPin() {
        return pin;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getEmail() {
        return email;
    }
}