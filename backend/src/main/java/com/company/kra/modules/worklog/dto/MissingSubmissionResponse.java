package com.company.kra.modules.worklog.dto;

import java.time.LocalDate;

/** Who has not filed a day yet — the manager dashboard's first question. */
public record MissingSubmissionResponse(Long employeeId, String employeeName, String employeeCode,
                                        String departmentName, LocalDate workDate,
                                        boolean startedButNotSubmitted, int draftMinutes) {
}
