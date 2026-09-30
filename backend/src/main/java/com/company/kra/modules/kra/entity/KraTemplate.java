package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import com.company.kra.common.enums.PeriodType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What a role is measured on. Assigning a template copies it onto the
 * employee, so editing a template never rewrites an existing assignment.
 */
@Getter
@Setter
@Entity
@Table(name = "kra_templates")
public class KraTemplate extends CompanyScopedEntity {

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "department_id")
    private Long departmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType = PeriodType.QUARTERLY;

    /** Only a published template can be assigned. */
    @Column(name = "is_published", nullable = false)
    private boolean published;

    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<KraTemplateItem> items = new ArrayList<>();

    public BigDecimal totalWeightage() {
        return items.stream()
                .map(KraTemplateItem::getWeightage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
