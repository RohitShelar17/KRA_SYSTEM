package com.company.kra.modules.worklog.validation;

import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.worklog.entity.DailyWorkLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Every rule a work-log entry must satisfy, in one place.
 *
 * <p>Rules are checked in the order a person would notice them, so the first
 * message they see is the one that actually blocks them. Nothing here is
 * hard-coded: working hours, overtime allowance and the back-dating window all
 * come from {@link CompanySettings}, with the employee's own shift taking
 * precedence when one is set.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TimesheetValidator {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** The window an employee is contracted to, resolved per employee. */
    public record ShiftWindow(LocalTime start, LocalTime end, int netMinutes) {

        public int grossMinutes() {
            return (int) Duration.between(start, end).toMinutes();
        }
    }

    public ShiftWindow resolveShift(Employee employee, CompanySettings settings) {
        LocalTime start = employee.getShiftStartTime() != null
                ? employee.getShiftStartTime() : settings.getWorkStartTime();
        LocalTime end = employee.getShiftEndTime() != null
                ? employee.getShiftEndTime() : settings.getWorkEndTime();

        if (!end.isAfter(start)) {
            // A misconfigured shift must not silently disable the window checks.
            log.warn("Employee {} has an invalid shift ({} to {}); falling back to company hours",
                    employee.getId(), start, end);
            start = settings.getWorkStartTime();
            end = settings.getWorkEndTime();
        }
        int gross = (int) Duration.between(start, end).toMinutes();
        return new ShiftWindow(start, end, Math.max(gross - settings.getBreakMinutes(), 0));
    }

    /**
     * Rules that apply to the date itself, before any time is considered.
     *
     * @param reopened whether a manager has granted this person a live
     *                 exception for this date; the caller looks that up,
     *                 because this class stays free of repositories
     */
    public void validateDate(LocalDate workDate, CompanySettings settings, boolean reopened) {
        LocalDate today = LocalDate.now();

        if (workDate.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "You cannot log work for a future date");
        }
        int window = settings.getBackdateWindowDays();
        if (workDate.isBefore(today.minusDays(window)) && !reopened) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This date is outside the " + window + "-day back-dating window. "
                            + "Ask your reporting manager to reopen it for you.");
        }
    }

    /**
     * Validates one entry against the day it belongs to.
     *
     * @param existing every other entry already recorded for that employee and date
     */
    public void validateEntry(LocalTime startTime, LocalTime endTime, List<DailyWorkLog> existing,
                              ShiftWindow shift, CompanySettings settings) {

        if (endTime.equals(startTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Start and end time cannot be the same");
        }
        if (endTime.isBefore(startTime)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "End time must be after start time");
        }

        int duration = minutesBetween(startTime, endTime);
        if (duration < 5) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "An entry must cover at least 5 minutes");
        }

        validateWithinWorkingWindow(startTime, endTime, shift, settings);
        validateNoOverlap(startTime, endTime, existing);
        validateDailyCap(duration, existing, shift, settings);
    }

    private void validateWithinWorkingWindow(LocalTime startTime, LocalTime endTime,
                                             ShiftWindow shift, CompanySettings settings) {
        boolean outsideWindow = startTime.isBefore(shift.start()) || endTime.isAfter(shift.end());
        if (outsideWindow && !settings.isOvertimeAllowed()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Entries must fall inside working hours (" + shift.start().format(TIME)
                            + " to " + shift.end().format(TIME) + ")");
        }
    }

    /**
     * Half-open interval test, so slots that merely touch — 11:00-13:00 and
     * 13:00-14:00 — are not treated as a clash.
     */
    private void validateNoOverlap(LocalTime startTime, LocalTime endTime, List<DailyWorkLog> existing) {
        for (DailyWorkLog entry : existing) {
            if (entry.overlaps(startTime, endTime)) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "This time overlaps an existing entry ("
                                + entry.getStartTime().format(TIME) + " to "
                                + entry.getEndTime().format(TIME) + ": " + entry.getTitle() + ")");
            }
        }
    }

    private void validateDailyCap(int newDuration, List<DailyWorkLog> existing,
                                  ShiftWindow shift, CompanySettings settings) {
        int already = existing.stream().mapToInt(DailyWorkLog::getDurationMinutes).sum();
        int total = already + newDuration;
        int cap = shift.grossMinutes()
                + (settings.isOvertimeAllowed() ? settings.getMaxOvertimeMinutes() : 0);

        if (total > cap) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That would take the day to " + formatHours(total) + ", above the "
                            + formatHours(cap) + " limit for a single day");
        }
    }

    public int minutesBetween(LocalTime start, LocalTime end) {
        return (int) Duration.between(start, end).toMinutes();
    }

    private String formatHours(int minutes) {
        return String.format("%.1f h", minutes / 60.0);
    }
}
