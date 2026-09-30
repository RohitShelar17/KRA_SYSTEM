package com.company.kra.modules.performance.entity;

import com.company.kra.common.enums.PerformanceScope;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Append-only record of every manual score change. */
@Getter
@Setter
@Entity
@Table(name = "performance_adjustments")
public class PerformanceAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 20)
    private PerformanceScope snapshotType;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "previous_score", precision = 5, scale = 2)
    private BigDecimal previousScore;

    @Column(name = "new_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal newScore;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "adjusted_by")
    private Long adjustedBy;

    @Column(name = "adjusted_by_name", length = 150)
    private String adjustedByName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
