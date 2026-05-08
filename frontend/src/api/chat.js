import request from '../utils/request';

export const createSession = (title) =>
  request.post('/chat/session', null, { params: { title } });

export const getSessions = () => request.get('/chat/sessions');

export const getMessages = (sessionId) =>
  request.get(`/chat/session/${sessionId}/messages`);

export const sendMessage = (data) => request.post('/chat/send', data);

export const deleteSession = (sessionId) =>
  request.delete(`/chat/session/${sessionId}`);

export const setSystemPrompt = (sessionId, systemPrompt) =>
  request.patch(`/chat/session/${sessionId}/system-prompt`, { systemPrompt });

export const setKnowledgeBases = (sessionId, knowledgeBaseIds) =>
  request.put(`/chat/session/${sessionId}/knowledge-bases`, { knowledgeBaseIds });

export const addKnowledgeBase = (sessionId, kbId) =>
  request.post(`/chat/session/${sessionId}/knowledge-bases/${kbId}`);

export const removeKnowledgeBase = (sessionId, kbId) =>
  request.delete(`/chat/session/${sessionId}/knowledge-bases/${kbId}`);

export const getKnowledgeBases = (sessionId) =>
  request.get(`/chat/session/${sessionId}/knowledge-bases`);
