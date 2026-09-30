package com.company.kra.modules.performance.entity;

import com.company.kra.common.entity.CompanyScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "performance_rating_bands")
public class PerformanceRatingBand extends CompanyScopedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(name = "min_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal minScore;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    @Column(name = "colour_hex", length = 7)
    private String colourHex;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public boolean covers(BigDecimal score) {
        return score.compareTo(minScore) >= 0 && score.compareTo(maxScore) <= 0;
    }
}
