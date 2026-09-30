package com.company.kra.modules.organisation.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

public record CompanySettingsResponse(Long companyId, String companyName, LocalTime workStartTime,
                                      LocalTime workEndTime, int breakMinutes, String workingDays,
                                      boolean worklogApprovalEnabled, boolean overtimeAllowed,
                                      int maxOvertimeMinutes, int backdateWindowDays,
                                      LocalTime worklogCutoffTime, BigDecimal minSubmissionRatePct,
                                      int netWorkingMinutes) {
}
