package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.MeasurementType;
import com.company.kra.common.enums.TargetDirection;
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

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "employee_kpis")
public class EmployeeKpi extends CompanyScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_kra_id", nullable = false)
    private EmployeeKra employeeKra;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 20)
    private MeasurementType measurementType = MeasurementType.PERCENTAGE;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_direction", nullable = false, length = 20)
    private TargetDirection targetDirection = TargetDirection.HIGHER_IS_BETTER;

    @Column(length = 30)
    private String unit;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage;

    @Column(name = "cap_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal capPercent = new BigDecimal("120.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_source", nullable = false, length = 40)
    private ActualSource actualSource = ActualSource.MANUAL;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
