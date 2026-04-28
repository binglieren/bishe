import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { colors, spacing, typography } from '../theme';

/**
 * 带左侧装饰竖条的分区标题
 */
export default function SectionLabel({ children, icon, color = colors.primary }) {
  return (
    <View style={styles.row}>
      <View style={[styles.bar, { backgroundColor: color }]} />
      {icon ? <Text style={[styles.icon, { color }]}>{icon}</Text> : null}
      <Text style={styles.text}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: spacing.lg,
    marginBottom: spacing.md,
    gap: 8,
  },
  bar: { width: 3, height: 16, borderRadius: 2 },
  icon: { fontSize: 16 },
  text: {
    ...typography.titleMd,
    color: colors.textPrimary,
  },
});
