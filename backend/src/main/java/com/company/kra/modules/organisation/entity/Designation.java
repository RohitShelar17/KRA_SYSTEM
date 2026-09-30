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
@Table(name = "designations")
public class Designation extends CompanyScopedEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "grade_level", length = 20)
    private String gradeLevel;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(length = 400)
    private String description;
}
