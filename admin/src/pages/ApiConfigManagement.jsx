import { useEffect, useState } from 'react';
import {
  Card, Form, Input, InputNumber, Switch, Button, message, Tag, Space,
  Tooltip, Alert, Skeleton, Divider, Typography,
} from 'antd';
import {
  ApiOutlined, MessageOutlined, PictureOutlined,
  AudioOutlined, SoundOutlined, KeyOutlined, ReloadOutlined, SaveOutlined,
} from '@ant-design/icons';
import { getApiConfigs, updateApiConfig } from '../api/admin';

const { Paragraph, Text } = Typography;

/** 4 个环节的元数据：图标、标题、说明 */
const STAGE_META = {
  chat: {
    title: '文本对话',
    icon: <MessageOutlined />,
    color: '#1677ff',
    desc: 'AI 答疑文本路径、对话标题生成、题目知识点打标。推荐 deepseek-v4-pro / deepseek-v4-flash。',
    showChatFields: true,
  },
  multimodal: {
    title: '多模态对话',
    icon: <PictureOutlined />,
    color: '#722ed1',
    desc: '拍照搜题、题目图片结构化。需支持图片输入的模型（DeepSeek V4 / Gemini 3 / GLM-4V / Qwen-VL）。',
    showChatFields: true,
  },
  embedding: {
    title: '向量化（Embedding）',
    icon: <ApiOutlined />,
    color: '#13c2c2',
    desc: 'RAG 知识库检索、文档切片、题目向量化。推荐千问 text-embedding-v4（DB 列 1536 维，自动请求该维度）。',
    showChatFields: false,
  },
  audio: {
    title: '语音识别（STT）',
    icon: <AudioOutlined />,
    color: '#fa8c16',
    desc: '语音转文字。后端按 URL 自动选路：dashscope→Qwen-Omni（OpenAI 兼容 input_audio）；googleapis→Gemini Native。',
    showChatFields: false,
  },
  tts: {
    title: '语音合成（TTS）',
    icon: <SoundOutlined />,
    color: '#eb2f96',
    desc: 'AI 答案朗读。后端按 URL 自动选路：dashscope→CosyVoice（mp3）；googleapis→Gemini Native（24kHz WAV）。',
    showChatFields: false,
  },
};

const STAGE_ORDER = ['chat', 'multimodal', 'embedding', 'audio', 'tts'];

export default function ApiConfigManagement() {
  const [loading, setLoading] = useState(false);
  const [configs, setConfigs] = useState([]); // [{stage, ...}]

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await getApiConfigs();
      setConfigs(res.data || []);
    } catch (err) {
      message.error(err.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <h2 style={{ margin: 0 }}>全局 API 配置</h2>
          <Paragraph type="secondary" style={{ margin: '4px 0 0 0' }}>
            为每个调用环节单独配置 API 端点。配置仅由管理员修改，绝不在普通用户接口中暴露。
            解析优先级：<Text code>用户个人 AI 配置 → 本表 → application.yml 默认值</Text>。
          </Paragraph>
        </div>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
      </div>

      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="API Key 安全说明"
        description={
          <span>
            为保障安全，<b>已保存的 API Key 不会回传到前端</b>，仅显示前 4 位与后 4 位的脱敏预览。
            修改其他字段时，把 Key 输入框留空表示<b>保持原 Key 不变</b>；
            清空 Key 请点击「清除 API Key」按钮。
          </span>
        }
      />

      {loading && configs.length === 0 ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {STAGE_ORDER.map((stage) => {
            const data = configs.find((c) => c.stage === stage) || { stage };
            return (
              <StageCard
                key={stage}
                stage={stage}
                initial={data}
                onSaved={fetchData}
              />
            );
          })}
        </div>
      )}
    </div>
  );
}

function StageCard({ stage, initial, onSaved }) {
  const meta = STAGE_META[stage];
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  // 初始化表单（apiKey 永远不回填，由 placeholder 提示）
  useEffect(() => {
    form.setFieldsValue({
      apiUrl: initial.apiUrl || '',
      apiKey: '', // 永远空
      model: initial.model || '',
      temperature: initial.temperature ?? null,
      maxTokens: initial.maxTokens ?? null,
      systemPrompt: initial.systemPrompt || '',
      enabled: initial.enabled ?? true,
      description: initial.description || '',
    });
  }, [initial, form]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      // apiKey 三态处理：用户没填 → undefined → 不传 → 保持
      const payload = { ...values };
      if (!payload.apiKey) delete payload.apiKey;
      await updateApiConfig(stage, payload);
      message.success(`${meta.title} 已保存`);
      onSaved?.();
    } catch (err) {
      if (err?.errorFields) return; // 表单校验
      message.error(err.message || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleClearKey = async () => {
    try {
      setSaving(true);
      await updateApiConfig(stage, { apiKey: '' });
      message.success('API Key 已清除');
      onSaved?.();
    } catch (err) {
      message.error(err.message || '清除失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      title={
        <Space>
          <span style={{ color: meta.color, fontSize: 18 }}>{meta.icon}</span>
          <b>{meta.title}</b>
          <Tag>{stage}</Tag>
          {initial.enabled === false && <Tag color="default">已禁用</Tag>}
        </Space>
      }
      extra={
        <Space>
          {initial.apiKeySet ? (
            <Tooltip title={`已配置 Key：${initial.apiKeyPreview || '****'}`}>
              <Tag icon={<KeyOutlined />} color="success">已配置</Tag>
            </Tooltip>
          ) : (
            <Tag icon={<KeyOutlined />} color="warning">未配置</Tag>
          )}
        </Space>
      }
    >
      <Paragraph type="secondary" style={{ marginTop: -8 }}>{meta.desc}</Paragraph>
      <Form form={form} layout="vertical">
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
          <Form.Item
            label="API 基础地址（不含 /chat/completions 等后缀）"
            name="apiUrl"
            rules={[{ required: false }]}
          >
            <Input
              placeholder={
                stage === 'embedding' || stage === 'audio'
                  ? 'https://dashscope.aliyuncs.com/compatible-mode/v1'
                  : stage === 'tts'
                    ? 'https://dashscope.aliyuncs.com/api/v1'
                    : 'https://api.deepseek.com'
              }
              allowClear
            />
          </Form.Item>

          <Form.Item label="模型名" name="model">
            <Input
              placeholder={
                stage === 'embedding' ? 'text-embedding-v4'
                : stage === 'audio'   ? 'qwen3-omni-flash'
                : stage === 'tts'     ? 'cosyvoice-v3-flash'
                                      : 'deepseek-v4-pro'
              }
              allowClear
            />
          </Form.Item>
        </div>

        <Form.Item
          label={
            <Space>
              <span>API Key</span>
              <Tooltip title="留空 = 保持原 Key 不变；填写 = 替换为新 Key">
                <Text type="secondary" style={{ fontSize: 12 }}>（留空保持不变）</Text>
              </Tooltip>
            </Space>
          }
          name="apiKey"
        >
          <Input.Password
            placeholder={
              initial.apiKeySet
                ? `当前已配置：${initial.apiKeyPreview || '****'} （留空保持不变）`
                : 'API Key（DeepSeek 形如 sk-xxx，Gemini 形如 AIza...）'
            }
            autoComplete="new-password"
          />
        </Form.Item>

        {meta.showChatFields && (
          <>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
              <Form.Item label="温度 (temperature)" name="temperature">
                <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} placeholder="0.7" />
              </Form.Item>
              <Form.Item label="最大输出 tokens" name="maxTokens">
                <InputNumber min={1} max={32000} style={{ width: '100%' }} placeholder="2000" />
              </Form.Item>
            </div>
            <Form.Item label="系统提示词（System Prompt）" name="systemPrompt">
              <Input.TextArea rows={3} placeholder="留空使用默认提示词" />
            </Form.Item>
          </>
        )}

        <Form.Item label="备注" name="description">
          <Input placeholder="便于区分多套配置的备注信息" />
        </Form.Item>

        <Form.Item label="启用" name="enabled" valuePropName="checked">
          <Switch />
        </Form.Item>

        <Divider style={{ margin: '12px 0' }} />

        <Space>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
            保存
          </Button>
          {initial.apiKeySet && (
            <Button danger onClick={handleClearKey} loading={saving}>
              清除 API Key
            </Button>
          )}
        </Space>
      </Form>
    </Card>
  );
}
