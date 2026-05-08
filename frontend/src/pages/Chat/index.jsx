import { useEffect, useState, useRef } from 'react';
import { Input, Button, List, Card, message, Typography, Space, Popconfirm, Modal, Select, Tag, Tooltip } from 'antd';
import { SendOutlined, PlusOutlined, DeleteOutlined, RobotOutlined, UserOutlined, SettingOutlined, BookOutlined } from '@ant-design/icons';
import { getSessions, getMessages, sendMessage, createSession, deleteSession, setSystemPrompt, setKnowledgeBases } from '../../api/chat';
import { getKnowledgeBases } from '../../api/knowledgeBase';

const { Title, Paragraph, Text } = Typography;
const { TextArea } = Input;

export default function ChatPage() {
  const [sessions, setSessions] = useState([]);
  const [currentSessionId, setCurrentSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const [allKbs, setAllKbs] = useState([]);
  const [promptModalOpen, setPromptModalOpen] = useState(false);
  const [promptValue, setPromptValue] = useState('');
  const currentSession = sessions.find(s => s.id === currentSessionId);

  const loadSessions = async () => {
    try {
      const res = await getSessions();
      setSessions(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  const loadMessages = async (sessionId) => {
    try {
      const res = await getMessages(sessionId);
      setMessages(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  const loadKbs = async () => {
    try {
      const res = await getKnowledgeBases();
      setAllKbs(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadSessions(); loadKbs(); }, []);
  useEffect(() => { if (currentSessionId) loadMessages(currentSessionId); }, [currentSessionId]);
  useEffect(() => { messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);

  const handleSend = async () => {
    if (!inputValue.trim()) return;
    const userMsg = inputValue;
    setInputValue('');
    setMessages((prev) => [...prev, { role: 'user', content: userMsg }]);
    setLoading(true);
    try {
      const res = await sendMessage({ sessionId: currentSessionId, message: userMsg });
      if (!currentSessionId) {
        setCurrentSessionId(res.data.sessionId);
        loadSessions();
      }
      setMessages((prev) => [...prev, { role: 'assistant', content: res.data.content }]);
    } catch (err) {
      message.error('发送失败，请重试');
    } finally {
      setLoading(false);
    }
  };

  const handleNewSession = async () => {
    try {
      const res = await createSession();
      setCurrentSessionId(res.data.id);
      setMessages([]);
      loadSessions();
    } catch (err) {
      message.error('创建失败');
    }
  };

  const handleDeleteSession = async (id) => {
    try {
      await deleteSession(id);
      if (currentSessionId === id) {
        setCurrentSessionId(null);
        setMessages([]);
      }
      loadSessions();
    } catch (err) {
      message.error('删除失败');
    }
  };

  const handleKnowledgeBaseChange = async (Ids) => {
    if (!currentSessionId) return;
    try {
      await setKnowledgeBases(currentSessionId, Ids);
      loadSessions();
      message.success('已更新知识库绑定');
    } catch (err) {
      message.error('更新失败');
    }
  };

  const handlePromptSave = async () => {
    if (!currentSessionId) return;
    try {
      await setSystemPrompt(currentSessionId, promptValue || null);
      setPromptModalOpen(false);
      loadSessions();
      message.success('已保存系统指令');
    } catch (err) {
      message.error('保存失败');
    }
  };

  const openPromptModal = () => {
    setPromptValue(currentSession?.systemPrompt || '');
    setPromptModalOpen(true);
  };

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 200px)' }}>
      <div style={{ width: 260, borderRight: '1px solid #f0f0f0', paddingRight: 16, overflow: 'auto' }}>
        <Button type="primary" icon={<PlusOutlined />} block onClick={handleNewSession} style={{ marginBottom: 12 }}>
          新对话
        </Button>
        <List
          dataSource={sessions}
          renderItem={(item) => (
            <List.Item
              style={{
                cursor: 'pointer', padding: '8px 12px', borderRadius: 6,
                background: currentSessionId === item.id ? '#e6f4ff' : 'transparent',
              }}
              onClick={() => setCurrentSessionId(item.id)}
              actions={[
                <Popconfirm key="del" title="确定删除？" onConfirm={(e) => { e.stopPropagation(); handleDeleteSession(item.id); }}>
                  <DeleteOutlined onClick={(e) => e.stopPropagation()} />
                </Popconfirm>,
              ]}
            >
              <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {item.title || '新对话'}
              </div>
            </List.Item>
          )}
        />
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', paddingLeft: 16 }}>
        {/* 会话工具栏 */}
        {currentSessionId && (
          <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <Select
              mode="multiple"
              allowClear
              placeholder="绑定知识库"
              style={{ minWidth: 200, maxWidth: 350 }}
              value={currentSession?.knowledgeBaseIds || []}
              onChange={handleKnowledgeBaseChange}
              options={allKbs.map(kb => ({ label: kb.name, value: kb.id }))}
              maxTagCount={2}
            />
            <Tooltip title="设置系统指令">
              <Button
                icon={<SettingOutlined />}
                onClick={openPromptModal}
                type={currentSession?.systemPrompt ? 'primary' : 'default'}
                ghost={!!currentSession?.systemPrompt}
              >
                {currentSession?.systemPrompt ? '已设指令' : '系统指令'}
              </Button>
            </Tooltip>
            {currentSession?.knowledgeBaseIds?.length > 0 && (
              <span style={{ color: '#888', fontSize: 12 }}>
                <BookOutlined /> 已绑定 {currentSession.knowledgeBaseIds.length} 个知识库
              </span>
            )}
          </div>
        )}

        <div style={{ flex: 1, overflow: 'auto', marginBottom: 16 }}>
          {messages.length === 0 ? (
            <div style={{ textAlign: 'center', marginTop: 80, color: '#999' }}>
              <RobotOutlined style={{ fontSize: 48, marginBottom: 16 }} />
              <p>你好！我是 AI 考研助手，可以回答你上传资料中的问题。</p>
            </div>
          ) : (
            messages.map((msg, idx) => (
              <div key={idx} style={{ display: 'flex', marginBottom: 16, justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start' }}>
                <Card
                  size="small"
                  style={{
                    maxWidth: '70%',
                    background: msg.role === 'user' ? '#1677ff' : '#f5f5f5',
                    color: msg.role === 'user' ? '#fff' : '#000',
                  }}
                  styles={{ body: { color: msg.role === 'user' ? '#fff' : '#000' } }}
                >
                  <div style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</div>
                </Card>
              </div>
            ))
          )}
          <div ref={messagesEndRef} />
        </div>

        <div style={{ display: 'flex', gap: 8 }}>
          <TextArea
            rows={2}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); handleSend(); } }}
            placeholder="输入你的问题...（Enter 发送，Shift+Enter 换行）"
          />
          <Button type="primary" icon={<SendOutlined />} onClick={handleSend} loading={loading}
            style={{ height: 'auto' }}>
            发送
          </Button>
        </div>
      </div>

      {/* 系统指令编辑弹窗 */}
      <Modal
        title="会话系统指令"
        open={promptModalOpen}
        onOk={handlePromptSave}
        onCancel={() => setPromptModalOpen(false)}
        okText="保存"
        cancelText="取消"
        width={600}
      >
        <Text type="secondary" style={{ marginBottom: 8, display: 'block' }}>
          自定义 AI 在当前会话中的行为。例如："你是我的高等数学助教，请用通俗易懂的语言解释，多举例子。"
        </Text>
        <TextArea
          rows={6}
          value={promptValue}
          onChange={(e) => setPromptValue(e.target.value)}
          placeholder="留空则使用默认设置…"
        />
      </Modal>
    </div>
  );
}
