import { useEffect, useState } from 'react';
import { Card, Form, Input, InputNumber, Button, message, Typography, Popconfirm } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { getAiConfig, saveAiConfig, resetAiConfig } from '../../api/aiConfig';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

export default function AiConfigPage() {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);

  const loadConfig = async () => {
    try {
      const res = await getAiConfig();
      form.setFieldsValue(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadConfig(); }, []);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setLoading(true);
      await saveAiConfig(values);
      message.success('配置保存成功');
      loadConfig();
    } catch (err) {
      message.error(err.message || '保存失败');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = async () => {
    try {
      await resetAiConfig();
      message.success('已重置为默认配置');
      loadConfig();
    } catch (err) {
      message.error('重置失败');
    }
  };

  return (
    <div>
      <Title level={4}><SettingOutlined /> AI 模型配置</Title>
      <Paragraph type="secondary">
        在此配置你自己的 AI 模型参数，支持所有 OpenAI 兼容的 API（如 DeepSeek、Ollama 等）。留空则使用系统默认配置。
      </Paragraph>

      <Card>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item label="API 地址" name="apiUrl" extra="OpenAI 兼容的 API 地址，如 https://api.openai.com/v1">
            <Input placeholder="https://api.openai.com/v1" />
          </Form.Item>
          <Form.Item label="API Key" name="apiKey" extra="你的 API 密钥，已保存的密钥显示为 ******">
            <Input.Password placeholder="sk-..." />
          </Form.Item>
          <Form.Item label="对话模型" name="chatModel" extra="用于 AI 对话的模型名称">
            <Input placeholder="gpt-4o-mini" />
          </Form.Item>
          <Form.Item label="向量模型" name="embeddingModel" extra="用于文档向量化的模型名称，修改后需重新上传文档">
            <Input placeholder="text-embedding-3-small" />
          </Form.Item>
          <Form.Item label="Temperature" name="temperature" extra="生成随机性，0 更确定，1 更随机">
            <InputNumber min={0} max={2} step={0.1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Max Tokens" name="maxTokens" extra="单次回复最大 token 数">
            <InputNumber min={100} max={8000} step={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="系统提示词" name="systemPrompt" extra="自定义 AI 角色和行为提示词">
            <TextArea rows={4} placeholder="你是一个专业的考研辅导助手..." />
          </Form.Item>

          <div style={{ display: 'flex', gap: 12 }}>
            <Button type="primary" htmlType="submit" loading={loading}>保存配置</Button>
            <Popconfirm title="确定重置为默认配置？" onConfirm={handleReset}>
              <Button danger>重置为默认</Button>
            </Popconfirm>
          </div>
        </Form>
      </Card>
    </div>
  );
}