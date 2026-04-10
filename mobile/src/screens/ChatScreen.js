import React, { useEffect, useState, useRef } from 'react';
import { View, StyleSheet, FlatList, KeyboardAvoidingView, Platform, TextInput } from 'react-native';
import { Text, Card, Button, IconButton, Snackbar } from 'react-native-paper';
import { getSessions, getMessages, sendMessage, createSession, deleteSession } from '../api/chat';

export default function ChatScreen() {
  const [sessions, setSessions] = useState([]);
  const [currentSessionId, setCurrentSessionId] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

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
      setSnackMsg('发送失败，请重试');
      setSnackVisible(true);
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
      setSnackMsg('创建失败');
      setSnackVisible(true);
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
      setSnackMsg('删除失败');
      setSnackVisible(true);
    }
  };

  const renderSession = ({ item }) => (
    <View style={[styles.sessionItem, currentSessionId === item.id && styles.sessionActive]}>
      <Text
        style={styles.sessionTitle}
        numberOfLines={1}
        ellipsizeMode="tail"
        onPress={() => setCurrentSessionId(item.id)}
      >
        {item.title || '新对话'}
      </Text>
      <IconButton
        icon="delete"
        size={16}
        onPress={() => handleDeleteSession(item.id)}
      />
    </View>
  );

  const renderMessage = ({ item }) => (
    <View style={[styles.msgRow, item.role === 'user' ? styles.msgRowUser : styles.msgRowBot]}>
      <Card
        style={[
          styles.msgCard,
          item.role === 'user' ? styles.msgCardUser : styles.msgCardBot,
        ]}
      >
        <Card.Content>
          <Text style={[styles.msgText, item.role === 'user' && styles.msgTextUser]}>
            {item.content}
          </Text>
        </Card.Content>
      </Card>
    </View>
  );

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={80}
    >
      <View style={styles.sidebar}>
        <Button
          mode="contained"
          icon="plus"
          onPress={handleNewSession}
          style={styles.newBtn}
        >
          新对话
        </Button>
        <FlatList
          data={sessions}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderSession}
          style={styles.sessionList}
        />
      </View>

      <View style={styles.chatArea}>
        {messages.length === 0 ? (
          <View style={styles.emptyChat}>
            <IconButton icon="robot-outline" size={48} iconColor="#999" />
            <Text style={styles.emptyText}>你好！我是 AI 考研助手，可以回答你上传资料中的问题。</Text>
          </View>
        ) : (
          <FlatList
            data={messages}
            keyExtractor={(_, idx) => String(idx)}
            renderItem={renderMessage}
            contentContainerStyle={styles.msgList}
          />
        )}

        <View style={styles.inputRow}>
          <TextInput
            mode="outlined"
            value={inputValue}
            onChangeText={setInputValue}
            placeholder="输入你的问题..."
            multiline
            style={styles.input}
          />
          <Button
            mode="contained"
            onPress={handleSend}
            loading={loading}
            style={styles.sendBtn}
          >
            发送
          </Button>
        </View>
      </View>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    flexDirection: 'row',
    backgroundColor: '#f5f5f5',
  },
  sidebar: {
    width: 200,
    borderRightWidth: 1,
    borderRightColor: '#e0e0e0',
    padding: 8,
  },
  newBtn: {
    marginBottom: 8,
  },
  sessionList: {
    flex: 1,
  },
  sessionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 6,
    marginBottom: 2,
  },
  sessionActive: {
    backgroundColor: '#e6f4ff',
  },
  sessionTitle: {
    flex: 1,
    fontSize: 14,
  },
  chatArea: {
    flex: 1,
    padding: 8,
  },
  emptyChat: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyText: {
    color: '#999',
    textAlign: 'center',
    paddingHorizontal: 24,
  },
  msgList: {
    paddingBottom: 8,
  },
  msgRow: {
    marginBottom: 12,
    alignItems: 'flex-start',
  },
  msgRowUser: {
    alignItems: 'flex-end',
  },
  msgRowBot: {
    alignItems: 'flex-start',
  },
  msgCard: {
    maxWidth: '80%',
  },
  msgCardUser: {
    backgroundColor: '#1677ff',
  },
  msgCardBot: {
    backgroundColor: '#f0f0f0',
  },
  msgText: {
    fontSize: 14,
  },
  msgTextUser: {
    color: '#fff',
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 8,
    marginTop: 8,
  },
  input: {
    flex: 1,
  },
  sendBtn: {
    marginBottom: 6,
  },
});