import { createTheme, type Theme, type ThemeOptions } from '@mui/material/styles';
import { brand, elevation, fontStack, radius, semantic, slate } from './tokens';

/**
 * Precision Enterprise Performance, expressed as an MUI theme.
 *
 * Two rules carry the look: elevation comes from 1px borders rather than drop
 * shadows, and density is compact — 38px grid rows, 34px inputs — because these
 * screens are scanned, not read.
 */

declare module '@mui/material/styles' {
  interface Palette {
    tint: { sky: string; emerald: string; amber: string; rose: string };
  }
  interface PaletteOptions {
    tint?: { sky: string; emerald: string; amber: string; rose: string };
  }
}

const typography: ThemeOptions['typography'] = {
  fontFamily: fontStack,
  htmlFontSize: 16,
  fontSize: 13,
  h1: { fontSize: 36, fontWeight: 700, lineHeight: 1.22, letterSpacing: '-0.025em' },
  h2: { fontSize: 28, fontWeight: 700, lineHeight: 1.28, letterSpacing: '-0.02em' },
  h3: { fontSize: 24, fontWeight: 600, lineHeight: 1.33, letterSpacing: '-0.02em' },
  h4: { fontSize: 20, fontWeight: 600, lineHeight: 1.4, letterSpacing: '-0.015em' },
  h5: { fontSize: 16, fontWeight: 600, lineHeight: 1.5, letterSpacing: '-0.01em' },
  h6: { fontSize: 14, fontWeight: 600, lineHeight: 1.45 },
  subtitle1: { fontSize: 15, fontWeight: 500, lineHeight: 1.6 },
  subtitle2: { fontSize: 13, fontWeight: 600, lineHeight: 1.54 },
  body1: { fontSize: 15, fontWeight: 400, lineHeight: 1.6, letterSpacing: '-0.005em' },
  body2: { fontSize: 13, fontWeight: 400, lineHeight: 1.54 },
  caption: { fontSize: 12, fontWeight: 400, lineHeight: 1.33 },
  overline: {
    fontSize: 11,
    fontWeight: 600,
    lineHeight: 1.27,
    letterSpacing: '0.04em',
    textTransform: 'uppercase',
  },
  button: { fontSize: 13, fontWeight: 600, textTransform: 'none', letterSpacing: 0 },
};

function buildTheme(mode: 'light' | 'dark'): Theme {
  const isLight = mode === 'light';

  const canvas = isLight ? slate.s50 : slate.s900;
  const surface = isLight ? '#FFFFFF' : slate.s800;
  const subtle = isLight ? slate.s100 : '#243244';
  const border = isLight ? slate.s200 : slate.s700;
  const borderStrong = isLight ? slate.s300 : '#415168';
  const textPrimary = isLight ? slate.s900 : '#E8EDF5';
  const textSecondary = isLight ? slate.s600 : slate.s400;

  return createTheme({
    palette: {
      mode,
      primary: {
        main: brand.indigo700,
        light: brand.indigo500,
        dark: brand.indigo800,
        contrastText: '#FFFFFF',
      },
      secondary: { main: semantic.sky, contrastText: '#FFFFFF' },
      success: { main: semantic.emerald, contrastText: '#FFFFFF' },
      warning: { main: semantic.amber, contrastText: '#3B2708' },
      error: { main: semantic.rose, contrastText: '#FFFFFF' },
      info: { main: semantic.sky, contrastText: '#FFFFFF' },
      background: { default: canvas, paper: surface },
      text: { primary: textPrimary, secondary: textSecondary, disabled: slate.s400 },
      divider: border,
      tint: {
        sky: isLight ? semantic.skyTint : 'rgba(14,165,233,0.12)',
        emerald: isLight ? semantic.emeraldTint : 'rgba(16,185,129,0.12)',
        amber: isLight ? semantic.amberTint : 'rgba(245,158,11,0.12)',
        rose: isLight ? semantic.roseTint : 'rgba(239,68,68,0.12)',
      },
    },
    typography,
    shape: { borderRadius: radius.control },
    spacing: 4,
    components: {
      MuiCssBaseline: {
        styleOverrides: {
          html: { WebkitFontSmoothing: 'antialiased' },
          body: {
            backgroundColor: canvas,
            fontFeatureSettings: "'cv05' 1, 'cv11' 1",
          },
          // Tabular figures wherever numbers line up in columns.
          'td, th, .tabular': { fontVariantNumeric: 'tabular-nums' },
          '::-webkit-scrollbar': { width: 10, height: 10 },
          '::-webkit-scrollbar-thumb': {
            backgroundColor: isLight ? slate.s300 : slate.s700,
            borderRadius: 8,
            border: `2px solid ${canvas}`,
          },
        },
      },
      MuiPaper: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: {
            backgroundImage: 'none',
            border: `1px solid ${border}`,
            borderRadius: radius.card,
          },
          elevation8: { boxShadow: elevation.popover },
        },
      },
      MuiCard: {
        defaultProps: { elevation: 0 },
        styleOverrides: {
          root: { border: `1px solid ${border}`, borderRadius: radius.card, boxShadow: elevation.card },
        },
      },
      MuiCardContent: {
        styleOverrides: { root: { padding: 16, '&:last-child': { paddingBottom: 16 } } },
      },
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: {
          root: { borderRadius: radius.control, paddingInline: 14, minHeight: 34 },
          sizeSmall: { minHeight: 30, paddingInline: 10, fontSize: 12.5 },
          sizeLarge: { minHeight: 42, fontSize: 14 },
          contained: { '&:hover': { backgroundColor: brand.indigo600 } },
          outlined: {
            borderColor: border,
            color: textPrimary,
            backgroundColor: surface,
            '&:hover': { borderColor: borderStrong, backgroundColor: canvas },
          },
          text: { color: textSecondary, '&:hover': { backgroundColor: subtle, color: textPrimary } },
        },
      },
      MuiIconButton: {
        styleOverrides: { root: { borderRadius: radius.control } },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            backgroundColor: surface,
            borderRadius: radius.control,
            fontSize: 13,
            '& fieldset': { borderColor: borderStrong },
            '&:hover fieldset': { borderColor: isLight ? slate.s400 : '#4C5D75' },
            '&.Mui-focused fieldset': { borderWidth: 2, borderColor: brand.indigo700 },
          },
          input: { paddingBlock: 9 },
          inputSizeSmall: { paddingBlock: 7.5 },
        },
      },
      MuiInputLabel: { styleOverrides: { root: { fontSize: 13 } } },
      MuiFormHelperText: { styleOverrides: { root: { marginLeft: 2, fontSize: 11.5 } } },
      MuiMenuItem: { styleOverrides: { root: { fontSize: 13, minHeight: 34 } } },
      MuiChip: {
        styleOverrides: {
          root: { height: 22, fontSize: 11, fontWeight: 600, borderRadius: radius.pill },
          label: { paddingInline: 8 },
        },
      },
      MuiTable: { defaultProps: { size: 'small' } },
      MuiTableHead: {
        styleOverrides: {
          root: {
            backgroundColor: isLight ? slate.s50 : '#1A2536',
            '& .MuiTableCell-root': {
              fontSize: 11,
              fontWeight: 600,
              letterSpacing: '0.04em',
              textTransform: 'uppercase',
              color: isLight ? slate.s500 : slate.s400,
              borderBottom: `1px solid ${border}`,
              height: 36,
              whiteSpace: 'nowrap',
            },
          },
        },
      },
      MuiTableCell: {
        styleOverrides: {
          root: { borderBottom: `1px solid ${isLight ? slate.s100 : '#243244'}`, fontSize: 13 },
          sizeSmall: { padding: '8px 12px' },
        },
      },
      MuiTableRow: {
        styleOverrides: {
          root: { '&:hover': { backgroundColor: isLight ? slate.s50 : '#1B2637' } },
        },
      },
      MuiTooltip: {
        styleOverrides: {
          tooltip: {
            backgroundColor: slate.s900,
            fontSize: 11.5,
            borderRadius: radius.control,
            padding: '6px 9px',
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: { borderRadius: radius.overlay, boxShadow: elevation.modal },
        },
      },
      MuiDrawer: { styleOverrides: { paper: { backgroundImage: 'none' } } },
      MuiAlert: {
        styleOverrides: { root: { borderRadius: radius.control, border: `1px solid ${border}` } },
      },
      MuiTabs: {
        styleOverrides: {
          root: { minHeight: 40, borderBottom: `1px solid ${border}` },
          indicator: { height: 2, backgroundColor: brand.indigo700 },
        },
      },
      MuiTab: {
        styleOverrides: {
          root: { minHeight: 40, fontSize: 13, fontWeight: 500, textTransform: 'none' },
        },
      },
      MuiLinearProgress: {
        styleOverrides: { root: { height: 6, borderRadius: 3, backgroundColor: subtle } },
      },
      MuiListItemButton: {
        styleOverrides: { root: { borderRadius: radius.control } },
      },
      MuiDivider: { styleOverrides: { root: { borderColor: border } } },
    },
  });
}

export const lightTheme = buildTheme('light');
export const darkTheme = buildTheme('dark');
