import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, TextInput, Button, Snackbar } from 'react-native-paper';
import { getAiConfig, saveAiConfig, resetAiConfig } from '../api/aiConfig';
import { colors, radii, spacing, shadows, typography } from '../theme';
import ModernCard from '../components/ModernCard';
import GradientHeader from '../components/GradientHeader';
import SectionLabel from '../components/SectionLabel';

export default function AiConfigScreen() {
  const [apiUrl, setApiUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [chatModel, setChatModel] = useState('');
  const [embeddingModel, setEmbeddingModel] = useState('');
  const [embeddingApiUrl, setEmbeddingApiUrl] = useState('');
  const [embeddingApiKey, setEmbeddingApiKey] = useState('');
  const [temperature, setTemperature] = useState('0.7');
  const [maxTokens, setMaxTokens] = useState('2000');
  const [systemPrompt, setSystemPrompt] = useState('');
  const [loading, setLoading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const loadConfig = async () => {
    try {
      const res = await getAiConfig();
      const data = res.data;
      setApiUrl(data.apiUrl || '');
      setApiKey(data.apiKey || '');
      setChatModel(data.chatModel || '');
      setEmbeddingModel(data.embeddingModel || '');
      setEmbeddingApiUrl(data.embeddingApiUrl || '');
      setEmbeddingApiKey(data.embeddingApiKey || '');
      setTemperature(data.temperature != null ? String(data.temperature) : '0.7');
      setMaxTokens(data.maxTokens != null ? String(data.maxTokens) : '2000');
      setSystemPrompt(data.systemPrompt || '');
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadConfig(); }, []);

  const handleSave = async () => {
    setLoading(true);
    try {
      await saveAiConfig({
        apiUrl: apiUrl || undefined,
        apiKey: apiKey && apiKey !== '******' ? apiKey : undefined,
        chatModel: chatModel || undefined,
        embeddingModel: embeddingModel || undefined,
        embeddingApiUrl: embeddingApiUrl || undefined,
        embeddingApiKey: embeddingApiKey && embeddingApiKey !== '******' ? embeddingApiKey : undefined,
        temperature: temperature ? parseFloat(temperature) : undefined,
        maxTokens: maxTokens ? parseInt(maxTokens) : undefined,
        systemPrompt: systemPrompt || undefined,
      });
      setSnackMsg('✅ 配置保存成功');
      setSnackVisible(true);
      loadConfig();
    } catch (err) {
      setSnackMsg(err.message || '保存失败');
      setSnackVisible(true);
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async () => {
    try {
      await resetAiConfig();
      setSnackMsg('已重置为默认配置');
      setSnackVisible(true);
      loadConfig();
    } catch (err) {
      setSnackMsg('重置失败');
      setSnackVisible(true);
    }
  };

  const inputProps = {
    mode: 'outlined',
    style: styles.input,
    outlineColor: colors.border,
    activeOutlineColor: colors.primary,
  };

  return (
    <View style={styles.container}>
      <ScrollView contentContainerStyle={styles.content}>
        <GradientHeader
          icon="🧠"
          title="AI 模型配置"
          subtitle="自定义对话与向量模型，所有 OpenAI 兼容 API 可用"
        />

        <View style={styles.infoBanner}>
          <Text style={styles.infoBannerIcon}>💡</Text>
          <Text style={styles.infoBannerText}>
            留空则使用系统默认配置。支持 DeepSeek、OpenAI、Ollama、智谱 GLM 等。
          </Text>
        </View>

        <ModernCard style={styles.card} accent={colors.primary} padding={18}>
          <SectionLabel icon="💬" color={colors.primary}>对话 / 多模态端点</SectionLabel>
          <TextInput
            {...inputProps}
            label="API 地址"
            value={apiUrl}
            onChangeText={setApiUrl}
            placeholder="https://api.openai.com/v1"
          />
          <TextInput
            {...inputProps}
            label="API Key"
            value={apiKey}
            onChangeText={setApiKey}
            secureTextEntry
            placeholder="sk-..."
          />
          <TextInput
            {...inputProps}
            label="对话模型"
            value={chatModel}
            onChangeText={setChatModel}
            placeholder="gpt-4o-mini"
          />

          <SectionLabel icon="🔎" color={colors.info}>Embedding 端点</SectionLabel>
          <View style={styles.hintBox}>
            <Text style={styles.hintText}>
              题目向量化 / RAG 专用。留空则复用上方对话端点。{'\n'}
              推荐 OpenAI：<Text style={styles.mono}>text-embedding-3-small</Text>
            </Text>
          </View>
          <TextInput
            {...inputProps}
            label="Embedding API 地址"
            value={embeddingApiUrl}
            onChangeText={setEmbeddingApiUrl}
            placeholder="https://api.openai.com/v1"
          />
          <TextInput
            {...inputProps}
            label="Embedding API Key"
            value={embeddingApiKey}
            onChangeText={setEmbeddingApiKey}
            secureTextEntry
            placeholder="sk-..."
          />
          <TextInput
            {...inputProps}
            label="向量模型"
            value={embeddingModel}
            onChangeText={setEmbeddingModel}
            placeholder="text-embedding-3-small"
          />

          <SectionLabel icon="🎛️" color={colors.secondary}>生成参数</SectionLabel>
          <View style={styles.row}>
            <TextInput
              {...inputProps}
              style={[styles.input, styles.half]}
              label="Temperature"
              value={temperature}
              onChangeText={setTemperature}
              keyboardType="decimal-pad"
              placeholder="0.7"
            />
            <TextInput
              {...inputProps}
              style={[styles.input, styles.half]}
              label="Max Tokens"
              value={maxTokens}
              onChangeText={setMaxTokens}
              keyboardType="number-pad"
              placeholder="2000"
            />
          </View>
          <TextInput
            {...inputProps}
            label="系统提示词"
            value={systemPrompt}
            onChangeText={setSystemPrompt}
            multiline
            numberOfLines={4}
            placeholder="你是一个专业的考研辅导助手..."
          />

          <View style={styles.buttonRow}>
            <Button
              mode="contained"
              onPress={handleSave}
              loading={loading}
              style={[styles.btn, styles.saveBtn]}
              contentStyle={styles.btnContent}
              labelStyle={styles.btnLabel}
            >
              保存配置
            </Button>
            <Button
              mode="outlined"
              onPress={handleReset}
              textColor={colors.danger}
              style={[styles.btn, styles.resetBtn]}
              contentStyle={styles.btnContent}
              labelStyle={styles.btnLabel}
            >
              重置默认
            </Button>
          </View>
        </ModernCard>
      </ScrollView>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  content: {
    paddingBottom: spacing['2xl'],
  },
  infoBanner: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: colors.primarySoft,
    borderRadius: radii.md,
    padding: spacing.md,
    marginHorizontal: spacing.lg,
    marginBottom: spacing.md,
    gap: 8,
  },
  infoBannerIcon: {
    fontSize: 16,
  },
  infoBannerText: {
    ...typography.bodySm,
    color: colors.primaryDark,
    flex: 1,
    lineHeight: 19,
  },
  card: {
    marginHorizontal: spacing.lg,
  },
  hintBox: {
    backgroundColor: colors.infoSoft,
    borderRadius: radii.sm,
    padding: spacing.sm + 2,
    marginBottom: spacing.md,
  },
  hintText: {
    ...typography.caption,
    color: '#1E40AF',
    lineHeight: 18,
  },
  mono: {
    fontFamily: 'monospace',
    fontWeight: '600',
  },
  input: {
    marginBottom: spacing.md,
    backgroundColor: colors.surface,
  },
  row: {
    flexDirection: 'row',
    gap: 10,
  },
  half: {
    flex: 1,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: spacing.md,
    marginTop: spacing.sm,
  },
  btn: {
    flex: 1,
    borderRadius: radii.md,
  },
  btnContent: {
    paddingVertical: 4,
  },
  btnLabel: {
    fontSize: 14,
    fontWeight: '600',
  },
  saveBtn: {
    ...shadows.colored,
  },
  resetBtn: {
    borderColor: colors.danger,
  },
});
