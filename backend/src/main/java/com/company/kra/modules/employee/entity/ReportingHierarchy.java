package com.company.kra.modules.employee.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.RelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Effective-dated manager relationship. A past appraisal must still resolve
 * the manager who actually held the relationship at that time, so manager
 * changes append a row here rather than only overwriting the employee record.
 */
@Getter
@Setter
@Entity
@Table(name = "reporting_hierarchy")
public class ReportingHierarchy extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "manager_id", nullable = false)
    private Long managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 20)
    private RelationType relationType = RelationType.PRIMARY;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;
}
