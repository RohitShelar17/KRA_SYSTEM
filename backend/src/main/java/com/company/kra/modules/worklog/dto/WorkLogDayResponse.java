package com.company.kra.modules.worklog.dto;

import com.company.kra.common.enums.WorkLogDayStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Everything the daily work log screen needs in one call. */
public record WorkLogDayResponse(Long id, Long employeeId, String employeeName, String employeeCode,
                                 LocalDate workDate, String dayOfWeek, WorkLogDayStatus status,
                                 boolean editable, boolean holiday, String holidayName,
                                 LocalTime shiftStart, LocalTime shiftEnd,
                                 DayTotalsResponse totals,
                                 List<WorkLogEntryResponse> entries,
                                 List<TimelineSegmentResponse> timeline,
                                 List<ApprovalHistoryResponse> history,
                                 LocalDateTime submittedAt, String reviewedByName,
                                 LocalDateTime reviewedAt, String reviewComments,
                                 boolean approvalRequired) {
}
