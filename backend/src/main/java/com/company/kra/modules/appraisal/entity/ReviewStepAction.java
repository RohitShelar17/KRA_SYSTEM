package com.company.kra.modules.appraisal.entity;

import com.company.kra.common.enums.StepAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Append-only trail of every workflow move on an appraisal. */
@Getter
@Setter
@Entity
@Table(name = "review_step_actions")
public class ReviewStepAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "appraisal_id", nullable = false)
    private Long appraisalId;

    @Column(name = "step_type", nullable = false, length = 40)
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepAction action;

    @Column(name = "from_step", length = 40)
    private String fromStep;

    @Column(name = "to_step", length = 40)
    private String toStep;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(length = 1000)
    private String comments;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
