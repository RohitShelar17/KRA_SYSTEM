package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.WorkLogDayStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Row shape for the review queue and the range views. */
public record WorkLogDaySummaryResponse(Long id, Long employeeId, String employeeName, String employeeCode,
                                        String departmentName, LocalDate workDate, WorkLogDayStatus status,
                                        int loggedMinutes, int productiveMinutes, int officeMinutes,
                                        int unallocatedMinutes, int entryCount,
                                        LocalDateTime submittedAt, double loggedPercent) {
}
