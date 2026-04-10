import { useEffect, useState, useRef } from 'react';
import { Input, Button, List, Card, message, Typography, Space, Popconfirm } from 'antd';
import { SendOutlined, PlusOutlined, DeleteOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import { getSessions, getMessages, sendMessage, createSession, deleteSession } from '../../api/chat';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

export default function ChatPage() {
  const [sessions, setSessions] = useState([]);
  const [currentSessionId, setCurrentSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef(null);

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

  useEffect(() => { loadSessions(); }, []);
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

  return (
    <div style={{ display: 'flex', height: 'calc(100vh - 200px)' }}>
      {/* 会话列表 */}
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

      {/* 对话区 */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', paddingLeft: 16 }}>
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
    </div>
  );
}
