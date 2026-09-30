package com.company.kra.modules.worklog.entity;

import com.company.kra.common.enums.ApprovalAction;
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

/** Append-only history of every state change on a work-log day. */
@Getter
@Setter
@Entity
@Table(name = "work_log_approvals")
public class WorkLogApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "work_log_day_id", nullable = false)
    private Long workLogDayId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ApprovalAction action;

    @Column(name = "from_status", nullable = false, length = 30)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 30)
    private String toStatus;

    @Column(name = "actor_employee_id")
    private Long actorEmployeeId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(length = 500)
    private String comments;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
