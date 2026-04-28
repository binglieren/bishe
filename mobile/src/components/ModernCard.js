import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { colors, radii, shadows } from '../theme';

/**
 * 现代化卡片：圆角 + 柔和阴影 + 可选左色带 + 可选点击反馈
 */
export default function ModernCard({
  children,
  style,
  accent,        // 左侧色条颜色
  onPress,
  elevation = 'md',  // sm | md | lg
  padding = 16,
}) {
  const content = (
    <View
      style={[
        styles.card,
        shadows[elevation],
        { padding },
        accent && { borderLeftWidth: 4, borderLeftColor: accent },
        style,
      ]}
    >
      {children}
    </View>
  );

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        android_ripple={{ color: colors.primarySoft }}
        style={({ pressed }) => [pressed && { opacity: 0.92, transform: [{ scale: 0.995 }] }]}
      >
        {content}
      </Pressable>
    );
  }
  return content;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    overflow: 'hidden',
  },
});
