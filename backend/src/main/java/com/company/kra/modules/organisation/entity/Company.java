package com.company.kra.modules.organisation.entity;

import com.company.kra.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "companies")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 400)
    private String address;

    @Column(nullable = false, length = 60)
    private String timezone = "Asia/Kolkata";

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "INR";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
