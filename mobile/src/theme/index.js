/**
 * 统一设计令牌 - 现代化的考研助手视觉系统
 * 参考 Material You + Tailwind 色彩哲学
 */

export const colors = {
  // 品牌主色 — 现代靛蓝，饱和度适中不刺眼
  primary:        '#4F46E5',   // indigo-600
  primaryLight:   '#818CF8',   // indigo-400
  primaryDark:    '#3730A3',   // indigo-800
  primarySoft:    '#EEF2FF',   // indigo-50，用于背景 / 徽标
  primaryOn:      '#FFFFFF',

  // 辅色 — 青绿（表示进步、正确）
  secondary:      '#10B981',   // emerald-500
  secondarySoft:  '#D1FAE5',

  // 语义色
  success:        '#10B981',
  successSoft:    '#D1FAE5',
  warning:        '#F59E0B',   // amber-500
  warningSoft:    '#FEF3C7',
  danger:         '#EF4444',   // red-500
  dangerSoft:     '#FEE2E2',
  info:           '#3B82F6',   // blue-500
  infoSoft:       '#DBEAFE',

  // 类别色（推荐卡左边框）
  weak:           '#EF4444',
  sibling:        '#F59E0B',
  related:        '#3B82F6',
  revisit:        '#8B5CF6',   // violet-500
  coldStart:      '#10B981',

  // 题型色
  single:         '#4F46E5',
  multi:          '#8B5CF6',
  blank:          '#06B6D4',
  short:          '#10B981',
  proof:          '#F59E0B',

  // 中性色（明灰阶，比 Material 默认柔和）
  background:     '#F8FAFC',   // slate-50
  surface:        '#FFFFFF',
  surfaceAlt:     '#F1F5F9',   // slate-100
  border:         '#E2E8F0',   // slate-200
  borderLight:    '#F1F5F9',
  textPrimary:    '#0F172A',   // slate-900
  textSecondary:  '#475569',   // slate-600
  textTertiary:   '#94A3B8',   // slate-400
  textDisabled:   '#CBD5E1',   // slate-300

  // 渐变端点（用于 Header 背景）
  gradientStart:  '#6366F1',   // indigo-500
  gradientEnd:    '#8B5CF6',   // violet-500
  gradientSoft1:  '#EEF2FF',
  gradientSoft2:  '#F5F3FF',
};

export const radii = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  pill: 999,
};

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 20,
  '2xl': 24,
  '3xl': 32,
};

// 层级阴影（跨平台）
export const shadows = {
  sm: {
    shadowColor: '#0F172A',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.06,
    shadowRadius: 2,
    elevation: 1,
  },
  md: {
    shadowColor: '#0F172A',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 10,
    elevation: 3,
  },
  lg: {
    shadowColor: '#0F172A',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.12,
    shadowRadius: 20,
    elevation: 6,
  },
  colored: {
    shadowColor: '#4F46E5',
    shadowOffset: { width: 0, height: 8 },
    shadowOpacity: 0.24,
    shadowRadius: 16,
    elevation: 6,
  },
};

export const typography = {
  displayLg: { fontSize: 32, fontWeight: '800', letterSpacing: -0.5 },
  displayMd: { fontSize: 26, fontWeight: '700', letterSpacing: -0.3 },
  titleLg:   { fontSize: 20, fontWeight: '700' },
  titleMd:   { fontSize: 17, fontWeight: '600' },
  titleSm:   { fontSize: 15, fontWeight: '600' },
  bodyLg:    { fontSize: 16, lineHeight: 24 },
  bodyMd:    { fontSize: 14, lineHeight: 21 },
  bodySm:    { fontSize: 13, lineHeight: 19 },
  caption:   { fontSize: 12, lineHeight: 16 },
  overline:  { fontSize: 11, fontWeight: '600', letterSpacing: 1, textTransform: 'uppercase' },
};

// react-native-paper 主题
export const paperTheme = {
  colors: {
    primary: colors.primary,
    onPrimary: colors.primaryOn,
    primaryContainer: colors.primarySoft,
    onPrimaryContainer: colors.primaryDark,
    secondary: colors.secondary,
    onSecondary: '#FFFFFF',
    secondaryContainer: colors.secondarySoft,
    onSecondaryContainer: '#065F46',
    tertiary: '#8B5CF6',
    background: colors.background,
    onBackground: colors.textPrimary,
    surface: colors.surface,
    onSurface: colors.textPrimary,
    surfaceVariant: colors.surfaceAlt,
    onSurfaceVariant: colors.textSecondary,
    outline: colors.border,
    outlineVariant: colors.borderLight,
    error: colors.danger,
    onError: '#FFFFFF',
    errorContainer: colors.dangerSoft,
    onErrorContainer: '#991B1B',
    elevation: {
      level0: 'transparent',
      level1: colors.surface,
      level2: colors.surface,
      level3: colors.surface,
      level4: colors.surface,
      level5: colors.surface,
    },
  },
  roundness: radii.md,
};

export const theme = { colors, radii, spacing, shadows, typography };
export default theme;
