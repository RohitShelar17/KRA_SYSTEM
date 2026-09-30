package com.company.kra.modules.performance.entity;

import com.company.kra.common.entity.BaseEntity;
import com.company.kra.common.enums.PerformanceParameter;
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
@Table(name = "performance_formula_parameters")
public class PerformanceFormulaParameter extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_id", nullable = false)
    private PerformanceFormulaConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "parameter_code", nullable = false, length = 40)
    private PerformanceParameter parameterCode;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
