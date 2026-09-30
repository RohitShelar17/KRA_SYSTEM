package com.company.kra.modules.task.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.CategoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The activity vocabulary. A category decides which bucket a work-log entry
 * falls into (work, meeting, break) and whether it counts as productive time,
 * both of which the Phase 4 scoring engine reads straight off these rows.
 */
@Getter
@Setter
@Entity
@Table(name = "task_categories")
public class TaskCategory extends CompanyScopedEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", nullable = false, length = 20)
    private CategoryType categoryType = CategoryType.WORK;

    @Column(name = "is_productive", nullable = false)
    private boolean productive = true;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "colour_hex", length = 7)
    private String colourHex;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
