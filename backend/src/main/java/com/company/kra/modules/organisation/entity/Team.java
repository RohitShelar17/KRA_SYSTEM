package com.company.kra.modules.organisation.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "teams")
public class Team extends CompanyScopedEntity {

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Column(name = "team_lead_employee_id")
    private Long teamLeadEmployeeId;
}
