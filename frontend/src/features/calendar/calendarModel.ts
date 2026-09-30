/**
 * What a month looks like, worked out away from the rendering.
 *
 * The rule for "is this a working day" has to agree with the backend's
 * WorkingCalendar, because the same question decides both what the calendar
 * paints red and what the score engine expected. Two copies of the rule would
 * eventually disagree, and then somebody is chased for a day nothing ever
 * expected them to file. The shape of the rule is therefore copied
 * deliberately: the company's working-day tokens, minus holidays.
 */
import type { Holiday } from '@/types/domain';
import type { WorkLogDaySummary } from '@/types/work';
import { isoDate } from '@/utils/time';

export type DayKind =
  /** Before this person joined — nothing was ever expected. */
  | 'BEFORE_JOINING'
  /** Not in the company's working week. */
  | 'WEEK_OFF'
  /** A working weekday the company has declared off. */
  | 'HOLIDAY'
  /** A working day with a work-log row, whatever state it is in. */
  | 'FILED'
  /** A working day that has passed with nothing recorded at all. */
  | 'MISSED'
  /** A working day still to come. */
  | 'FUTURE';

export interface CalendarDay {
  date: string;
  dayOfMonth: number;
  /** False for the leading and trailing days that pad the grid to whole weeks. */
  inMonth: boolean;
  isToday: boolean;
  kind: DayKind;
  /** Null unless a work-log row exists for the day. */
  summary: WorkLogDaySummary | null;
  /** Set whenever the date is a declared holiday, even on a week-off. */
  holidayName: string | null;
  /** True when the day counts towards the month's expected hours. */
  working: boolean;
}

export interface MonthTotals {
  workingDays: number;
  filedDays: number;
  missedDays: number;
  approvedDays: number;
  awaitingDays: number;
  rejectedDays: number;
  loggedMinutes: number;
  productiveMinutes: number;
  expectedMinutes: number;
}

/** Monday-first, matching the en-GB week the rest of the UI reads in. */
export const WEEKDAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;

/** Java's TextStyle.SHORT in English, which is what `workingDays` holds. */
const DAY_TOKENS = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'] as const;

/** A day is awaiting somebody else once it leaves the filer's hands. */
const AWAITING = new Set(['SUBMITTED', 'UNDER_REVIEW']);

export interface MonthInput {
  year: number;
  /** 0-11, as JavaScript counts months. */
  month: number;
  today: string;
  /** "MON,TUE,WED,THU,FRI" from company settings. */
  workingDays: string;
  holidays: Holiday[];
  summaries: WorkLogDaySummary[];
  joiningDate: string | null;
}

/** The first and last dates the grid shows, so the caller can fetch exactly that window. */
export function monthGridRange(year: number, month: number): { from: string; to: string } {
  const first = new Date(year, month, 1);
  const from = new Date(first);
  from.setDate(first.getDate() - mondayOffset(first));

  const last = new Date(year, month + 1, 0);
  const to = new Date(last);
  to.setDate(last.getDate() + (6 - mondayOffset(last)));

  return { from: isoDate(from), to: isoDate(to) };
}

/**
 * Whole weeks from the Monday on or before the 1st to the Sunday on or after
 * the last — five or six rows, never a fixed six, so a short month does not
 * carry an empty trailing week.
 */
export function buildMonth(input: MonthInput): CalendarDay[] {
  const { from, to } = monthGridRange(input.year, input.month);
  const working = workingTokens(input.workingDays);
  const holidays = new Map(input.holidays.map((holiday) => [holiday.holidayDate, holiday.name]));
  const summaries = new Map(input.summaries.map((summary) => [summary.workDate, summary]));

  const days: CalendarDay[] = [];
  const cursor = new Date(`${from}T00:00:00`);
  const end = new Date(`${to}T00:00:00`);

  while (cursor <= end) {
    const date = isoDate(cursor);
    const holidayName = holidays.get(date) ?? null;
    const summary = summaries.get(date) ?? null;
    const isWorkingWeekday = working.has(DAY_TOKENS[cursor.getDay()]);
    const beforeJoining = input.joiningDate != null && date < input.joiningDate;

    days.push({
      date,
      dayOfMonth: cursor.getDate(),
      inMonth: cursor.getMonth() === input.month && cursor.getFullYear() === input.year,
      isToday: date === input.today,
      kind: classify({
        date,
        today: input.today,
        isWorkingWeekday,
        isHoliday: holidayName != null,
        beforeJoining,
        hasSummary: summary != null,
      }),
      summary,
      holidayName,
      working: isWorkingWeekday && holidayName == null && !beforeJoining,
    });

    cursor.setDate(cursor.getDate() + 1);
  }

  return days;
}

/**
 * Only days inside the month count: the padding belongs to its own month's
 * totals, and counting it twice would overstate every figure on the page.
 */
export function summariseMonth(days: CalendarDay[], netWorkingMinutes: number): MonthTotals {
  const totals: MonthTotals = {
    workingDays: 0,
    filedDays: 0,
    missedDays: 0,
    approvedDays: 0,
    awaitingDays: 0,
    rejectedDays: 0,
    loggedMinutes: 0,
    productiveMinutes: 0,
    expectedMinutes: 0,
  };

  for (const day of days) {
    if (!day.inMonth) {
      continue;
    }
    if (day.working) {
      totals.workingDays += 1;
      totals.expectedMinutes += netWorkingMinutes;
    }
    if (day.kind === 'MISSED') {
      totals.missedDays += 1;
    }
    if (day.summary) {
      totals.filedDays += 1;
      totals.loggedMinutes += day.summary.loggedMinutes;
      totals.productiveMinutes += day.summary.productiveMinutes;
      if (day.summary.status === 'APPROVED') {
        totals.approvedDays += 1;
      } else if (AWAITING.has(day.summary.status)) {
        totals.awaitingDays += 1;
      } else if (day.summary.status === 'REJECTED' || day.summary.status === 'MODIFICATION_REQUESTED') {
        totals.rejectedDays += 1;
      }
    }
  }

  return totals;
}

export function monthLabel(year: number, month: number): string {
  return new Date(year, month, 1).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
}

function classify(day: {
  date: string;
  today: string;
  isWorkingWeekday: boolean;
  isHoliday: boolean;
  beforeJoining: boolean;
  hasSummary: boolean;
}): DayKind {
  if (day.beforeJoining) {
    return 'BEFORE_JOINING';
  }
  // Week-off is checked before holiday so a public holiday landing on a Sunday
  // reads as the Sunday it already was, rather than as a day off from a day off.
  if (!day.isWorkingWeekday) {
    return 'WEEK_OFF';
  }
  if (day.isHoliday) {
    return 'HOLIDAY';
  }
  // A row in any state counts as filed: a draft is a day somebody has started,
  // not a day they missed, and colouring it red would be a lie.
  if (day.hasSummary) {
    return 'FILED';
  }
  return day.date > day.today ? 'FUTURE' : 'MISSED';
}

function workingTokens(workingDays: string): Set<string> {
  return new Set(
    workingDays
      .toUpperCase()
      .split(/[,\s]+/)
      .map((token) => token.trim())
      .filter(Boolean),
  );
}

/** 0 for Monday through 6 for Sunday. */
function mondayOffset(date: Date): number {
  return (date.getDay() + 6) % 7;
}
