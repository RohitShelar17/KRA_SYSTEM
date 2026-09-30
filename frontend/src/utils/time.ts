/** Time helpers shared by the work log, tasks and dashboards. */

/** "09:00:00" or "09:00" to minutes since midnight. */
export function minutesOfDay(time: string): number {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + (minute || 0);
}

/** Minutes since midnight back to "HH:mm". */
export function fromMinutes(minutes: number): string {
  const clamped = Math.max(0, Math.min(24 * 60 - 1, Math.round(minutes)));
  const hour = Math.floor(clamped / 60);
  const minute = clamped % 60;
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
}

/** Trims a backend "HH:mm:ss" to the "HH:mm" people actually read. */
export function toClock(time: string | null | undefined): string {
  if (!time) {
    return '—';
  }
  const [hour, minute] = time.split(':');
  return `${hour}:${minute}`;
}

/** 150 to "2h 30m"; deliberately compact for table cells and chips. */
export function formatDuration(minutes: number): string {
  if (!minutes) {
    return '0m';
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) {
    return `${rest}m`;
  }
  return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
}

/** 150 to "2.5" for headline figures where a decimal reads better. */
export function toHours(minutes: number): string {
  return (minutes / 60).toFixed(1);
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

export function isoDate(date: Date): string {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60 * 1000).toISOString().slice(0, 10);
}

export function shiftDate(iso: string, days: number): string {
  const date = new Date(`${iso}T00:00:00`);
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

export function formatDayLabel(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', {
    weekday: 'long',
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

export function formatShortDate(iso: string): string {
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
  });
}
