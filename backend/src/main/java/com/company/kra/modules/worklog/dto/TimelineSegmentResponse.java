package com.company.kra.modules.worklog.dto;

import java.time.LocalTime;

/**
 * One band on the day ribbon. Gaps inside the shift are returned as explicit
 * UNALLOCATED segments rather than left for the client to infer, so every
 * consumer draws the same day.
 */
public record TimelineSegmentResponse(LocalTime startTime, LocalTime endTime, int durationMinutes,
                                      String label, String kind, String colourHex, Long entryId) {

    public static TimelineSegmentResponse gap(LocalTime start, LocalTime end, int minutes) {
        return new TimelineSegmentResponse(start, end, minutes, "Unallocated", "UNALLOCATED",
                "#FECACA", null);
    }
}
