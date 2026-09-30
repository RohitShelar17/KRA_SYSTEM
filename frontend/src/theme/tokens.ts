/**
 * Precision Enterprise Performance — design tokens.
 *
 * Ported from the approved design system: deep indigo primary, slate neutral
 * canvas, semantic triage accents, border-first elevation. Components read
 * these through the MUI theme rather than importing hex values directly.
 */

export const brand = {
  indigo900: '#312E81',
  indigo800: '#3730A3',
  indigo700: '#4338CA',
  indigo600: '#4F46E5',
  indigo500: '#6366F1',
  indigo100: '#E0E7FF',
  indigo50: '#EEF2FF',
} as const;

export const semantic = {
  sky: '#0EA5E9',
  skyTint: '#F0F9FF',
  skyInk: '#0369A1',
  skyBorder: '#BAE6FD',

  emerald: '#10B981',
  emeraldTint: '#ECFDF5',
  emeraldInk: '#065F46',
  emeraldBorder: '#A7F3D0',

  amber: '#F59E0B',
  amberTint: '#FFFBEB',
  amberInk: '#92400E',
  amberBorder: '#FDE68A',

  rose: '#EF4444',
  roseTint: '#FEF2F2',
  roseInk: '#991B1B',
  roseBorder: '#FECACA',
} as const;

export const slate = {
  s50: '#F8FAFC',
  s100: '#F1F5F9',
  s200: '#E2E8F0',
  s300: '#CBD5E1',
  s400: '#94A3B8',
  s500: '#64748B',
  s600: '#475569',
  s700: '#334155',
  s800: '#1E293B',
  s900: '#0F172A',
} as const;

/** Border-first elevation: outlines carry hierarchy, shadows stay ambient. */
export const elevation = {
  card: '0 1px 2px 0 rgba(15, 23, 42, 0.04)',
  popover: '0 4px 6px -1px rgba(15, 23, 42, 0.08), 0 2px 4px -2px rgba(15, 23, 42, 0.04)',
  modal: '0 20px 25px -5px rgba(15, 23, 42, 0.12), 0 8px 10px -6px rgba(15, 23, 42, 0.06)',
} as const;

export const radius = {
  control: 6,
  card: 8,
  overlay: 12,
  pill: 9999,
} as const;

export const density = {
  sidebarWidth: 248,
  sidebarCollapsedWidth: 68,
  topBarHeight: 60,
  compactRowHeight: 38,
  standardRowHeight: 44,
} as const;

export const fontStack =
  "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif";

/** Status chip palettes, keyed by the meaning rather than the colour. */
export type StatusTone = 'success' | 'active' | 'pending' | 'critical' | 'neutral';

export const statusTone: Record<StatusTone, { bg: string; fg: string; border: string }> = {
  success: { bg: semantic.emeraldTint, fg: semantic.emeraldInk, border: semantic.emeraldBorder },
  active: { bg: semantic.skyTint, fg: semantic.skyInk, border: semantic.skyBorder },
  pending: { bg: semantic.amberTint, fg: semantic.amberInk, border: semantic.amberBorder },
  critical: { bg: semantic.roseTint, fg: semantic.roseInk, border: semantic.roseBorder },
  neutral: { bg: slate.s100, fg: slate.s600, border: slate.s200 },
};

export const statusToneDark: Record<StatusTone, { bg: string; fg: string; border: string }> = {
  success: { bg: 'rgba(16,185,129,0.12)', fg: '#6EE7B7', border: 'rgba(16,185,129,0.35)' },
  active: { bg: 'rgba(14,165,233,0.12)', fg: '#7DD3FC', border: 'rgba(14,165,233,0.35)' },
  pending: { bg: 'rgba(245,158,11,0.12)', fg: '#FCD34D', border: 'rgba(245,158,11,0.35)' },
  critical: { bg: 'rgba(239,68,68,0.12)', fg: '#FCA5A5', border: 'rgba(239,68,68,0.35)' },
  neutral: { bg: 'rgba(148,163,184,0.12)', fg: slate.s400, border: 'rgba(148,163,184,0.3)' },
};
