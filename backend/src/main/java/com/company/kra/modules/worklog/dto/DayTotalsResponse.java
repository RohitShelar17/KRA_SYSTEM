package com.company.kra.modules.worklog.dto;

public record DayTotalsResponse(int officeMinutes, int loggedMinutes, int productiveMinutes,
                                int meetingMinutes, int breakMinutes, int unallocatedMinutes,
                                int overtimeMinutes, int entryCount, double loggedPercent,
                                double productivePercent) {
}
