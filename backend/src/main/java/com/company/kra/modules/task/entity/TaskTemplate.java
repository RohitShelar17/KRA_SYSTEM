package com.company.kra.modules.task.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.Priority;
import com.company.kra.common.enums.Recurrence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "task_templates")
public class TaskTemplate extends CompanyScopedEntity {

    @Column(name = "department_id")
    private Long departmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_category_id", nullable = false)
    private TaskCategory taskCategory;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "default_estimated_minutes")
    private Integer defaultEstimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 20)
    private Priority defaultPriority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Recurrence recurrence = Recurrence.NONE;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
