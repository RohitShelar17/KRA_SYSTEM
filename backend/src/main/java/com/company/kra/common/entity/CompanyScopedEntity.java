package com.company.kra.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Everything an organisation owns carries its company id. Version 1 runs a
 * single company, but no query is written without it, so multi-tenant support
 * later is a filter change rather than a schema change.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class CompanyScopedEntity extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
