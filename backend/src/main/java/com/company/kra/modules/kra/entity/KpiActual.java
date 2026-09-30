package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.ActualSource;
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
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "kpi_actuals")
public class KpiActual extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_kpi_id", nullable = false)
    private EmployeeKpi employeeKpi;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "actual_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal actualValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ActualSource source = ActualSource.MANUAL;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "recorded_by_name", length = 150)
    private String recordedByName;

    @Column(length = 400)
    private String note;
}
