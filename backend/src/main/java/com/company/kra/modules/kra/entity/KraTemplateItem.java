package com.company.kra.modules.kra.entity;

import com.company.kra.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.ArrayList;
import java.util.List;

/** One key result area inside a template. */
@Getter
@Setter
@Entity
@Table(name = "kra_template_items")
public class KraTemplateItem extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kra_template_id", nullable = false)
    private KraTemplate template;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weightage;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    // Batched so loading a template's KPIs costs one extra query, not one per KRA.
    @BatchSize(size = 25)
    @OneToMany(mappedBy = "kraItem", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC, id ASC")
    private List<KpiTemplateItem> kpis = new ArrayList<>();

    public BigDecimal totalKpiWeightage() {
        return kpis.stream()
                .map(KpiTemplateItem::getWeightage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
