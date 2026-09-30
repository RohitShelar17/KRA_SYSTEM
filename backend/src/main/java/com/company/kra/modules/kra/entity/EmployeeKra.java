package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.KraStatus;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.modules.employee.entity.Employee;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A KRA as assigned to one employee for one period - a copy of the template
 * row, not a reference to it. The template can be revised tomorrow; what this
 * employee was measured on this quarter must not move.
 */
@Getter
@Setter
@Entity
@Table(name = "employee_kras")
public class EmployeeKra extends CompanyScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Kept for traceability only; the values here are the authority. */
    @Column(name = "kra_template_id")
    private Long kraTemplateId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType = PeriodType.QUARTERLY;

    @Column(name = "period_year", nullable = false)
    private short periodYear;

    /** 1-12 for monthly, 1-4 for quarterly, 1 for annual. */
    @Column(name = "period_index", nullable = false)
    private byte periodIndex;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KraStatus status = KraStatus.ACTIVE;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "assigned_by")
    private Long assignedBy;

    @BatchSize(size = 25)
    @OneToMany(mappedBy = "employeeKra", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<EmployeeKpi> kpis = new ArrayList<>();

    public String periodLabel() {
        return periodType.label(periodYear, periodIndex);
    }
}
