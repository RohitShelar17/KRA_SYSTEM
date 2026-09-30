package com.company.kra.modules.report.dto;

import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.enums.ReportRequestStatus;

import java.time.LocalDateTime;

/** An export request, whether it finished inline or is still being written. */
public record ReportRequestResponse(Long id, String reportKey, String reportTitle,
                                    ReportFormat format, ReportRequestStatus status,
                                    Integer rowCount, String fileName, Long byteSize,
                                    String errorDetail, String requestedByName,
                                    LocalDateTime createdAt, LocalDateTime completedAt,
                                    String downloadUrl) {
}
