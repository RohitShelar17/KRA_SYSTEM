package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceScope;

import java.math.BigDecimal;
import java.util.List;

/** A series for the trend chart, plus where the employee stands right now. */
public record PerformanceTrendResponse(Long employeeId, String employeeName, PerformanceScope scope,
                                       BigDecimal latestScore, String latestBandName,
                                       BigDecimal previousScore, BigDecimal changeFromPrevious,
                                       List<Point> points) {

    public record Point(String label, BigDecimal score, String bandName, String bandColour,
                        boolean frozen) {
    }
}
