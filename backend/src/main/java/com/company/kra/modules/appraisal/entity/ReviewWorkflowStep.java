package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.WorkflowStepType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One configurable stage of the review flow.
 *
 * <p>A step that is inactive is not part of the flow at all; a step that is
 * active but not required is offered and can be skipped, which is how an
 * organisation without department heads still gets a working appraisal.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "review_workflow_steps")
public class ReviewWorkflowStep extends CompanyScopedEntity {

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 40)
    private WorkflowStepType stepType;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 400)
    private String description;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(name = "sla_days")
    private Integer slaDays;
}
