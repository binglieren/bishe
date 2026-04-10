import request from './request';

export const createSession = (title) =>
  request.post('/chat/session', null, { params: { title } });

export const getSessions = () => request.get('/chat/sessions');

export const getMessages = (sessionId) =>
  request.get(`/chat/session/${sessionId}/messages`);

export const sendMessage = (data) => request.post('/chat/send', data);

export const deleteSession = (sessionId) =>
  request.delete(`/chat/session/${sessionId}`);