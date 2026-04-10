import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, TextInput, Button, Card, Snackbar } from 'react-native-paper';
import { getAiConfig, saveAiConfig, resetAiConfig } from '../api/aiConfig';

export default function AiConfigScreen() {
  const [apiUrl, setApiUrl] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [chatModel, setChatModel] = useState('');
  const [embeddingModel, setEmbeddingModel] = useState('');
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
        temperature: temperature ? parseFloat(temperature) : undefined,
        maxTokens: maxTokens ? parseInt(maxTokens) : undefined,
        systemPrompt: systemPrompt || undefined,
      });
      setSnackMsg('配置保存成功');
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

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.content}>
      <Text style={styles.title}>AI 模型配置</Text>
      <Text style={styles.desc}>配置你自己的 AI 模型参数，支持所有 OpenAI 兼容 API（DeepSeek、Ollama 等）。留空则使用系统默认配置。</Text>

      <Card style={styles.card}>
        <Card.Content>
          <TextInput
            label="API 地址"
            value={apiUrl}
            onChangeText={setApiUrl}
            mode="outlined"
            style={styles.input}
            placeholder="https://api.openai.com/v1"
          />
          <TextInput
            label="API Key"
            value={apiKey}
            onChangeText={setApiKey}
            mode="outlined"
            style={styles.input}
            secureTextEntry
            placeholder="sk-..."
          />
          <TextInput
            label="对话模型"
            value={chatModel}
            onChangeText={setChatModel}
            mode="outlined"
            style={styles.input}
            placeholder="gpt-4o-mini"
          />
          <TextInput
            label="向量模型"
            value={embeddingModel}
            onChangeText={setEmbeddingModel}
            mode="outlined"
            style={styles.input}
            placeholder="text-embedding-3-small"
          />
          <TextInput
            label="Temperature"
            value={temperature}
            onChangeText={setTemperature}
            mode="outlined"
            style={styles.input}
            keyboardType="decimal-pad"
            placeholder="0.7"
          />
          <TextInput
            label="Max Tokens"
            value={maxTokens}
            onChangeText={setMaxTokens}
            mode="outlined"
            style={styles.input}
            keyboardType="number-pad"
            placeholder="2000"
          />
          <TextInput
            label="系统提示词"
            value={systemPrompt}
            onChangeText={setSystemPrompt}
            mode="outlined"
            style={styles.input}
            multiline
            numberOfLines={4}
            placeholder="你是一个专业的考研辅导助手..."
          />

          <View style={styles.buttonRow}>
            <Button mode="contained" onPress={handleSave} loading={loading} style={styles.saveBtn}>
              保存配置
            </Button>
            <Button mode="outlined" onPress={handleReset} textColor="#f5222d" style={styles.resetBtn}>
              重置为默认
            </Button>
          </View>
        </Card.Content>
      </Card>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  content: {
    padding: 12,
    paddingBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    marginBottom: 8,
  },
  desc: {
    fontSize: 13,
    color: '#999',
    marginBottom: 16,
    lineHeight: 20,
  },
  card: {
    marginBottom: 12,
  },
  input: {
    marginBottom: 12,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 8,
  },
  saveBtn: {
    flex: 1,
  },
  resetBtn: {
    flex: 1,
    borderColor: '#f5222d',
  },
});