import React, { useState } from 'react';
import { View, StyleSheet, KeyboardAvoidingView, Platform, Text as RNText } from 'react-native';
import { Text, TextInput, Button, Snackbar } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { login } from '../api/auth';
import { colors, radii, spacing, shadows, typography } from '../theme';
import ModernCard from '../components/ModernCard';

export default function LoginScreen({ navigation }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const handleLogin = async () => {
    if (!username || !password) {
      setSnackMsg('请输入用户名和密码');
      setSnackVisible(true);
      return;
    }
    setLoading(true);
    try {
      const res = await login({ username, password });
      await AsyncStorage.setItem('token', res.data.token);
      await AsyncStorage.setItem('username', res.data.username);
      navigation.replace('Main');
    } catch (err) {
      setSnackMsg(err.message || '登录失败');
      setSnackVisible(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      {/* 模拟渐变背景 */}
      <View style={[styles.bgLayer, { backgroundColor: colors.gradientStart }]} />
      <View style={[styles.bgLayer, { backgroundColor: colors.gradientEnd, opacity: 0.55 }]} />
      <View style={styles.bgDot1} />
      <View style={styles.bgDot2} />

      <View style={styles.hero}>
        <View style={styles.logoWrap}>
          <RNText style={styles.logoIcon}>🎓</RNText>
        </View>
        <Text style={styles.heroTitle}>AI 考研助手</Text>
        <Text style={styles.heroSubtitle}>智能推荐 · 知识图谱 · 精准提分</Text>
      </View>

      <ModernCard style={styles.card} elevation="lg" padding={24}>
        <Text style={styles.formTitle}>欢迎回来</Text>
        <Text style={styles.formHint}>登录以继续你的备考之旅</Text>

        <TextInput
          label="用户名"
          value={username}
          onChangeText={setUsername}
          mode="outlined"
          style={styles.input}
          outlineColor={colors.border}
          activeOutlineColor={colors.primary}
          left={<TextInput.Icon icon="account" />}
        />
        <TextInput
          label="密码"
          value={password}
          onChangeText={setPassword}
          mode="outlined"
          secureTextEntry
          style={styles.input}
          outlineColor={colors.border}
          activeOutlineColor={colors.primary}
          left={<TextInput.Icon icon="lock" />}
        />
        <Button
          mode="contained"
          onPress={handleLogin}
          loading={loading}
          style={styles.button}
          contentStyle={styles.buttonContent}
          labelStyle={styles.buttonLabel}
        >
          登 录
        </Button>
        <Button
          mode="text"
          onPress={() => navigation.navigate('Register')}
          style={styles.link}
          textColor={colors.primary}
        >
          还没有账号？立即注册 →
        </Button>
      </ModernCard>

      <Snackbar
        visible={snackVisible}
        onDismiss={() => setSnackVisible(false)}
        duration={3000}
      >
        {snackMsg}
      </Snackbar>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    padding: spacing.xl,
  },
  bgLayer: {
    ...StyleSheet.absoluteFillObject,
  },
  bgDot1: {
    position: 'absolute',
    top: -60,
    right: -40,
    width: 220,
    height: 220,
    borderRadius: 110,
    backgroundColor: '#FFFFFF',
    opacity: 0.08,
  },
  bgDot2: {
    position: 'absolute',
    bottom: -80,
    left: -40,
    width: 180,
    height: 180,
    borderRadius: 90,
    backgroundColor: '#FFFFFF',
    opacity: 0.07,
  },
  hero: {
    alignItems: 'center',
    marginBottom: spacing['2xl'],
  },
  logoWrap: {
    width: 72,
    height: 72,
    borderRadius: radii.xl,
    backgroundColor: 'rgba(255,255,255,0.18)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: spacing.lg,
  },
  logoIcon: {
    fontSize: 40,
  },
  heroTitle: {
    ...typography.displayMd,
    color: '#FFFFFF',
    letterSpacing: 1,
  },
  heroSubtitle: {
    ...typography.bodySm,
    color: 'rgba(255,255,255,0.86)',
    marginTop: spacing.xs,
    letterSpacing: 0.5,
  },
  card: {
    backgroundColor: colors.surface,
  },
  formTitle: {
    ...typography.titleLg,
    color: colors.textPrimary,
    marginBottom: 4,
  },
  formHint: {
    ...typography.bodySm,
    color: colors.textSecondary,
    marginBottom: spacing.xl,
  },
  input: {
    marginBottom: spacing.md,
    backgroundColor: colors.surface,
  },
  button: {
    marginTop: spacing.md,
    borderRadius: radii.md,
    ...shadows.colored,
  },
  buttonContent: {
    paddingVertical: 6,
  },
  buttonLabel: {
    fontSize: 15,
    fontWeight: '700',
    letterSpacing: 2,
  },
  link: {
    marginTop: spacing.sm,
  },
});
