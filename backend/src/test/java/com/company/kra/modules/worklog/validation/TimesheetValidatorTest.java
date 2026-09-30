package com.company.kra.modules.worklog.validation;

import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.task.entity.TaskCategory;
import com.company.kra.modules.worklog.entity.DailyWorkLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The timesheet rules are the reason work-log data can be trusted, so each one
 * is tested on its own rather than through the service.
 */
class TimesheetValidatorTest {

    private final TimesheetValidator validator = new TimesheetValidator();

    @Nested
    @DisplayName("Time slot rules")
    class TimeSlots {

        @Test
        @DisplayName("End before start is rejected")
        void endBeforeStartRejected() {
            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(14, 0), LocalTime.of(11, 0),
                    List.of(), shift(), settings()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("End time must be after start time");
        }

        @Test
        @DisplayName("A zero-length slot is rejected")
        void zeroLengthRejected() {
            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(11, 0), LocalTime.of(11, 0),
                    List.of(), shift(), settings()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("cannot be the same");
        }

        @Test
        @DisplayName("A slot under five minutes is rejected")
        void tooShortRejected() {
            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(11, 0), LocalTime.of(11, 3),
                    List.of(), shift(), settings()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("at least 5 minutes");
        }

        @Test
        @DisplayName("A clean slot inside the shift is accepted")
        void validSlotAccepted() {
            assertThatCode(() -> validator.validateEntry(LocalTime.of(9, 0), LocalTime.of(11, 0),
                    List.of(), shift(), settings()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Overlap detection")
    class Overlaps {

        @Test
        @DisplayName("A slot inside an existing entry is rejected")
        void containedSlotRejected() {
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(9, 0), LocalTime.of(11, 0)));

            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(9, 30), LocalTime.of(10, 30),
                    existing, shift(), settings()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("overlaps an existing entry");
        }

        @Test
        @DisplayName("A slot straddling the end of an existing entry is rejected")
        void straddlingSlotRejected() {
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(9, 0), LocalTime.of(11, 0)));

            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(10, 30), LocalTime.of(12, 0),
                    existing, shift(), settings()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("overlaps");
        }

        @Test
        @DisplayName("A slot that fully covers an existing entry is rejected")
        void envelopingSlotRejected() {
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(10, 0), LocalTime.of(11, 0)));

            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(9, 0), LocalTime.of(12, 0),
                    existing, shift(), settings()))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("Touching slots do not collide: 11:00-13:00 then 13:00-14:00")
        void touchingSlotsAllowed() {
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(11, 0), LocalTime.of(13, 0)));

            assertThatCode(() -> validator.validateEntry(LocalTime.of(13, 0), LocalTime.of(14, 0),
                    existing, shift(), settings()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("A slot ending exactly where another begins is allowed")
        void slotEndingAtExistingStartAllowed() {
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(14, 0), LocalTime.of(16, 0)));

            assertThatCode(() -> validator.validateEntry(LocalTime.of(13, 0), LocalTime.of(14, 0),
                    existing, shift(), settings()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Working window and daily cap")
    class Limits {

        @Test
        @DisplayName("Working outside the shift is refused when overtime is off")
        void outsideWindowRejectedWithoutOvertime() {
            CompanySettings settings = settings();
            settings.setOvertimeAllowed(false);

            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(19, 0), LocalTime.of(20, 0),
                    List.of(), shift(), settings))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("inside working hours");
        }

        @Test
        @DisplayName("Working outside the shift is allowed when overtime is on")
        void outsideWindowAllowedWithOvertime() {
            assertThatCode(() -> validator.validateEntry(LocalTime.of(19, 0), LocalTime.of(20, 0),
                    List.of(), shift(), settings()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("The day cannot exceed the shift plus the overtime allowance")
        void dailyCapEnforced() {
            CompanySettings settings = settings();
            settings.setMaxOvertimeMinutes(60);
            // 9 hours already logged; the cap is 9h shift + 1h overtime.
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(9, 0), LocalTime.of(18, 0)));

            assertThatThrownBy(() -> validator.validateEntry(LocalTime.of(18, 0), LocalTime.of(20, 0),
                    existing, shift(), settings))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("limit for a single day");
        }

        @Test
        @DisplayName("Filling exactly up to the cap is allowed")
        void exactlyAtCapAllowed() {
            CompanySettings settings = settings();
            settings.setMaxOvertimeMinutes(60);
            List<DailyWorkLog> existing = List.of(entry(LocalTime.of(9, 0), LocalTime.of(18, 0)));

            assertThatCode(() -> validator.validateEntry(LocalTime.of(18, 0), LocalTime.of(19, 0),
                    existing, shift(), settings))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Date rules")
    class Dates {

        @Test
        @DisplayName("A future date is refused")
        void futureDateRejected() {
            assertThatThrownBy(() -> validator.validateDate(LocalDate.now().plusDays(1), settings(), false))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("future date");
        }

        @Test
        @DisplayName("A date beyond the back-dating window is refused")
        void tooOldRejected() {
            CompanySettings settings = settings();
            settings.setBackdateWindowDays(3);

            assertThatThrownBy(() -> validator.validateDate(LocalDate.now().minusDays(4), settings, false))
                    .isInstanceOf(BusinessException.class)
                    .extracting(caught -> ((BusinessException) caught).getErrorCode())
                    .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        @Test
        @DisplayName("A reopened date is accepted despite being outside the window")
        void reopenedDateAccepted() {
            CompanySettings settings = settings();
            settings.setBackdateWindowDays(3);

            assertThatCode(() -> validator.validateDate(LocalDate.now().minusDays(40), settings, true))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Reopening does not make a future date loggable")
        void reopeningDoesNotAllowTheFuture() {
            // The window is about how late you may file. Tomorrow has not
            // happened, and no grant should make it fileable.
            assertThatThrownBy(() -> validator.validateDate(LocalDate.now().plusDays(1), settings(), true))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("future date");
        }

        @Test
        @DisplayName("Today and dates inside the window are accepted")
        void recentDatesAccepted() {
            assertThatCode(() -> validator.validateDate(LocalDate.now(), settings(), false))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validateDate(LocalDate.now().minusDays(3), settings(), false))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Shift resolution")
    class Shifts {

        @Test
        @DisplayName("An employee shift overrides the company hours")
        void employeeShiftWins() {
            Employee employee = new Employee();
            employee.setShiftStartTime(LocalTime.of(11, 0));
            employee.setShiftEndTime(LocalTime.of(20, 0));

            TimesheetValidator.ShiftWindow window = validator.resolveShift(employee, settings());

            assertThat(window.start()).isEqualTo(LocalTime.of(11, 0));
            assertThat(window.end()).isEqualTo(LocalTime.of(20, 0));
            assertThat(window.grossMinutes()).isEqualTo(540);
            assertThat(window.netMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("Company hours are used when the employee has no shift")
        void fallsBackToCompanyHours() {
            TimesheetValidator.ShiftWindow window = validator.resolveShift(new Employee(), settings());

            assertThat(window.start()).isEqualTo(LocalTime.of(9, 0));
            assertThat(window.end()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("An inverted employee shift falls back instead of disabling the checks")
        void invalidShiftFallsBack() {
            Employee employee = new Employee();
            employee.setShiftStartTime(LocalTime.of(20, 0));
            employee.setShiftEndTime(LocalTime.of(9, 0));

            TimesheetValidator.ShiftWindow window = validator.resolveShift(employee, settings());

            assertThat(window.start()).isEqualTo(LocalTime.of(9, 0));
            assertThat(window.end()).isEqualTo(LocalTime.of(18, 0));
        }
    }

    @Test
    @DisplayName("Duration is measured in whole minutes")
    void durationInMinutes() {
        assertThat(validator.minutesBetween(LocalTime.of(9, 0), LocalTime.of(11, 30))).isEqualTo(150);
    }

    // ------------------------------------------------------------- fixtures

    private CompanySettings settings() {
        CompanySettings settings = new CompanySettings();
        settings.setWorkStartTime(LocalTime.of(9, 0));
        settings.setWorkEndTime(LocalTime.of(18, 0));
        settings.setBreakMinutes(60);
        settings.setOvertimeAllowed(true);
        settings.setMaxOvertimeMinutes(180);
        settings.setBackdateWindowDays(3);
        return settings;
    }

    private TimesheetValidator.ShiftWindow shift() {
        return new TimesheetValidator.ShiftWindow(LocalTime.of(9, 0), LocalTime.of(18, 0), 480);
    }

    private DailyWorkLog entry(LocalTime start, LocalTime end) {
        DailyWorkLog entry = new DailyWorkLog();
        entry.setStartTime(start);
        entry.setEndTime(end);
        entry.setDurationMinutes((int) java.time.Duration.between(start, end).toMinutes());
        entry.setTitle("Existing work");
        TaskCategory category = new TaskCategory();
        category.setName("Development");
        entry.setTaskCategory(category);
        return entry;
    }
}
