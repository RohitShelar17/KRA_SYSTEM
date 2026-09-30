package com.company.kra.modules.task.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.ProjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "projects")
public class Project extends CompanyScopedEntity {

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "project_manager_id")
    private Long projectManagerId;

    @Column(name = "client_name", length = 150)
    private String clientName;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    @Column(nullable = false)
    private boolean billable;
}
