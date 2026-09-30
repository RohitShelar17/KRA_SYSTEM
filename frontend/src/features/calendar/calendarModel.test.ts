import { describe, expect, it } from 'vitest';
import { buildMonth, monthGridRange, summariseMonth, type MonthInput } from './calendarModel';
import type { Holiday } from '@/types/domain';
import type { WorkLogDaySummary } from '@/types/work';

/**
 * These rules decide what a person is told they missed, so they are worth
 * pinning down: an off-by-one here is an employee chased for a Sunday.
 */
describe('calendar month model', () => {
  const MON_TO_FRI = 'MON,TUE,WED,THU,FRI';

  function input(overrides: Partial<MonthInput> = {}): MonthInput {
    return {
      year: 2026,
      month: 8, // September 2026: the 1st is a Tuesday, the 30th a Wednesday.
      today: '2026-09-15',
      workingDays: MON_TO_FRI,
      holidays: [],
      summaries: [],
      joiningDate: null,
      ...overrides,
    };
  }

  function holiday(date: string, name: string): Holiday {
    return { id: 1, holidayDate: date, name, holidayType: 'PUBLIC', dayOfWeek: 'MONDAY' };
  }

  function summary(date: string, overrides: Partial<WorkLogDaySummary> = {}): WorkLogDaySummary {
    return {
      id: 1,
      employeeId: 1,
      employeeName: 'Priya Nair',
      employeeCode: 'EMP-0003',
      departmentName: 'IT',
      workDate: date,
      status: 'APPROVED',
      loggedMinutes: 480,
      productiveMinutes: 420,
      officeMinutes: 480,
      unallocatedMinutes: 0,
      entryCount: 4,
      submittedAt: `${date}T18:10:00`,
      loggedPercent: 100,
      ...overrides,
    };
  }

  it('pads the grid to whole Monday-to-Sunday weeks', () => {
    // 1 September 2026 is a Tuesday, so the grid opens on Monday 31 August
    // and closes on Sunday 4 October.
    expect(monthGridRange(2026, 8)).toEqual({ from: '2026-08-31', to: '2026-10-04' });

    const days = buildMonth(input());
    expect(days).toHaveLength(35);
    expect(days[0].date).toBe('2026-08-31');
    expect(days[0].inMonth).toBe(false);
    expect(days.at(-1)?.date).toBe('2026-10-04');
  });

  it('marks a past working day with no row as missed, and a future one as future', () => {
    const days = buildMonth(input());
    const byDate = new Map(days.map((day) => [day.date, day]));

    expect(byDate.get('2026-09-10')?.kind).toBe('MISSED');
    expect(byDate.get('2026-09-16')?.kind).toBe('FUTURE');
    // Today itself is not yet missed — the day is not over.
    expect(byDate.get('2026-09-15')?.kind).toBe('MISSED');
  });

  it('treats a draft as filed rather than missed', () => {
    const days = buildMonth(input({ summaries: [summary('2026-09-10', { status: 'DRAFT' })] }));
    const day = days.find((candidate) => candidate.date === '2026-09-10');

    expect(day?.kind).toBe('FILED');
    expect(day?.summary?.status).toBe('DRAFT');
  });

  it('reads a holiday on a working weekday as a holiday, and one on a weekend as the weekend', () => {
    const days = buildMonth(
      input({
        holidays: [holiday('2026-09-17', 'Ganesh Chaturthi'), holiday('2026-09-19', 'Company day')],
      }),
    );
    const byDate = new Map(days.map((day) => [day.date, day]));

    // Thursday 17 September.
    expect(byDate.get('2026-09-17')?.kind).toBe('HOLIDAY');
    expect(byDate.get('2026-09-17')?.working).toBe(false);
    // Saturday 19 September was never a working day to begin with.
    expect(byDate.get('2026-09-19')?.kind).toBe('WEEK_OFF');
    // The name survives either way, so the tooltip can still say what it was.
    expect(byDate.get('2026-09-19')?.holidayName).toBe('Company day');
  });

  it('expects nothing before somebody joined', () => {
    const days = buildMonth(input({ joiningDate: '2026-09-14' }));
    const byDate = new Map(days.map((day) => [day.date, day]));

    expect(byDate.get('2026-09-10')?.kind).toBe('BEFORE_JOINING');
    expect(byDate.get('2026-09-10')?.working).toBe(false);
    expect(byDate.get('2026-09-14')?.kind).toBe('MISSED');
  });

  it('counts only the days inside the month', () => {
    const days = buildMonth(
      input({
        holidays: [holiday('2026-09-17', 'Ganesh Chaturthi')],
        summaries: [
          summary('2026-09-01'),
          summary('2026-09-02', { status: 'SUBMITTED', loggedMinutes: 450, productiveMinutes: 400 }),
          summary('2026-09-03', { status: 'REJECTED' }),
          // Inside the grid's leading week but not inside September.
          summary('2026-08-31', { loggedMinutes: 480 }),
        ],
      }),
    );
    const totals = summariseMonth(days, 480);

    // 22 weekdays in September 2026, less the one holiday.
    expect(totals.workingDays).toBe(21);
    expect(totals.expectedMinutes).toBe(21 * 480);
    expect(totals.filedDays).toBe(3);
    expect(totals.approvedDays).toBe(1);
    expect(totals.awaitingDays).toBe(1);
    expect(totals.rejectedDays).toBe(1);
    expect(totals.loggedMinutes).toBe(480 + 450 + 480);
  });

  it('honours a working week that is not Monday to Friday', () => {
    const days = buildMonth(input({ workingDays: 'MON,TUE,WED,THU,FRI,SAT' }));
    const byDate = new Map(days.map((day) => [day.date, day]));

    expect(byDate.get('2026-09-05')?.kind).toBe('MISSED'); // Saturday now counts.
    expect(byDate.get('2026-09-06')?.kind).toBe('WEEK_OFF'); // Sunday still does not.
  });
});
