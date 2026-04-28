import React, { useEffect, useState } from 'react';
import { View, StyleSheet, KeyboardAvoidingView, Platform } from 'react-native';
import { Text, TextInput, Button, Card, Snackbar } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { register } from '../api/auth';

const passwordRules = [
  { label: '至少 8 位字符', test: (p) => p.length >= 8 },
  { label: '包含小写字母', test: (p) => /[a-z]/.test(p) },
  { label: '包含大写字母', test: (p) => /[A-Z]/.test(p) },
  { label: '包含数字',     test: (p) => /\d/.test(p) },
];

export default function RegisterScreen({ navigation }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const handleRegister = async () => {
    if (!username || !password) {
      setSnackMsg('请输入用户名和密码');
      setSnackVisible(true);
      return;
    }
    if (password.length < 8) {
      setSnackMsg('密码至少8位');
      setSnackVisible(true);
      return;
    }
    if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(password)) {
      setSnackMsg('密码必须包含大写字母、小写字母和数字');
      setSnackVisible(true);
      return;
    }
    setLoading(true);
    try {
      const res = await register({ username, password, email });
      await AsyncStorage.setItem('token', res.data.token);
      await AsyncStorage.setItem('username', res.data.username);
      navigation.replace('Main');
    } catch (err) {
      setSnackMsg(err.message || '注册失败');
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
      <Card style={styles.card}>
        <Card.Content>
          <Text style={styles.title}>注册账号</Text>
          <TextInput
            label="用户名"
            value={username}
            onChangeText={setUsername}
            mode="outlined"
            style={styles.input}
            left={<TextInput.Icon icon="account" />}
          />
          <TextInput
            label="密码"
            value={password}
            onChangeText={setPassword}
            mode="outlined"
            secureTextEntry
            style={styles.input}
            left={<TextInput.Icon icon="lock" />}
          />
          <View style={styles.rulesBox}>
            {passwordRules.map((rule) => {
              const passed = rule.test(password);
              return (
                <View key={rule.label} style={styles.ruleRow}>
                  <Text style={[styles.ruleDot, passed ? styles.rulePassed : styles.ruleFailed]}>
                    {passed ? '✓' : '✗'}
                  </Text>
                  <Text style={[styles.ruleText, passed ? styles.rulePassed : styles.ruleFailed]}>
                    {rule.label}
                  </Text>
                </View>
              );
            })}
          </View>
          <TextInput
            label="邮箱（选填）"
            value={email}
            onChangeText={setEmail}
            mode="outlined"
            style={styles.input}
            left={<TextInput.Icon icon="email" />}
            keyboardType="email-address"
          />
          <Button
            mode="contained"
            onPress={handleRegister}
            loading={loading}
            style={styles.button}
          >
            注册
          </Button>
          <Button
            mode="text"
            onPress={() => navigation.goBack()}
            style={styles.link}
          >
            已有账号？去登录
          </Button>
        </Card.Content>
      </Card>
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
    backgroundColor: '#f0f2f5',
    padding: 20,
  },
  card: {
    borderRadius: 12,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 32,
    color: '#1677ff',
  },
  input: {
    marginBottom: 8,
  },
  rulesBox: {
    marginBottom: 12,
    paddingHorizontal: 4,
  },
  ruleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: 1,
  },
  ruleDot: {
    fontSize: 13,
    marginRight: 6,
    width: 14,
  },
  ruleText: {
    fontSize: 13,
  },
  rulePassed: {
    color: '#52c41a',
  },
  ruleFailed: {
    color: '#bfbfbf',
  },
  button: {
    marginTop: 8,
    paddingVertical: 4,
  },
  link: {
    marginTop: 8,
  },
});