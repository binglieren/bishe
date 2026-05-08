import request from '../utils/request';

export const getKnowledgeBases = () => request.get('/knowledge-base');

export const createKnowledgeBase = (data) => request.post('/knowledge-base', data);

export const updateKnowledgeBase = (id, data) => request.put(`/knowledge-base/${id}`, data);

export const deleteKnowledgeBase = (id) => request.delete(`/knowledge-base/${id}`);
