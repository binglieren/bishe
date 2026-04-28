import React from 'react';
import { View, StyleSheet, Text } from 'react-native';
import { colors, radii, spacing, typography } from '../theme';

/**
 * 模拟渐变 Header：无依赖，用两层不同透明度的色块叠加模拟渐变
 * （避免引入 expo-linear-gradient 新依赖）
 */
export default function GradientHeader({ title, subtitle, icon, right }) {
  return (
    <View style={styles.wrap}>
      {/* 底层 */}
      <View style={[styles.layer, { backgroundColor: colors.gradientStart }]} />
      {/* 顶层透明块模拟渐变 */}
      <View style={[styles.layer, styles.layerTop]} />
      {/* 装饰光斑 */}
      <View style={styles.dot1} />
      <View style={styles.dot2} />

      <View style={styles.content}>
        <View style={{ flex: 1 }}>
          <View style={styles.titleRow}>
            {icon ? <Text style={styles.icon}>{icon}</Text> : null}
            <Text style={styles.title}>{title}</Text>
          </View>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
        {right}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    borderBottomLeftRadius: radii.xl,
    borderBottomRightRadius: radii.xl,
    overflow: 'hidden',
    marginBottom: spacing.lg,
  },
  layer: {
    ...StyleSheet.absoluteFillObject,
  },
  layerTop: {
    backgroundColor: colors.gradientEnd,
    opacity: 0.5,
  },
  dot1: {
    position: 'absolute',
    right: -40,
    top: -40,
    width: 140,
    height: 140,
    borderRadius: 70,
    backgroundColor: '#FFFFFF',
    opacity: 0.08,
  },
  dot2: {
    position: 'absolute',
    left: -20,
    bottom: -30,
    width: 90,
    height: 90,
    borderRadius: 45,
    backgroundColor: '#FFFFFF',
    opacity: 0.06,
  },
  content: {
    paddingHorizontal: spacing.xl,
    paddingTop: spacing.xl,
    paddingBottom: spacing['2xl'],
    flexDirection: 'row',
    alignItems: 'center',
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  icon: { fontSize: 22 },
  title: {
    ...typography.titleLg,
    color: '#FFFFFF',
  },
  subtitle: {
    ...typography.bodySm,
    color: 'rgba(255,255,255,0.82)',
    marginTop: 4,
  },
});
