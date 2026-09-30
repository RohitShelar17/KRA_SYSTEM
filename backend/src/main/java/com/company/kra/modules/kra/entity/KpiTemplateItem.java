package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.BaseEntity;
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
@Table(name = "kpi_template_items")
public class KpiTemplateItem extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kra_template_item_id", nullable = false)
    private KraTemplateItem kraItem;

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

    @Column(name = "default_target_value", precision = 15, scale = 2)
    private BigDecimal defaultTargetValue;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage;

    /** Over-achievement is rewarded, but bounded. */
    @Column(name = "cap_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal capPercent = new BigDecimal("120.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "actual_source", nullable = false, length = 40)
    private ActualSource actualSource = ActualSource.MANUAL;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
