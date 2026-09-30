package com.company.kra.modules.organisation.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Departments are data, never code constants. The head is held as a plain id
 * so the organisation module does not depend on the employee module.
 */
@Getter
@Setter
@Entity
@Table(name = "departments")
public class Department extends CompanyScopedEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Column(name = "parent_department_id")
    private Long parentDepartmentId;

    @Column(name = "head_employee_id")
    private Long headEmployeeId;
}
