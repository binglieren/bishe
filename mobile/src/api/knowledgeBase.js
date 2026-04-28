import request from './request';

// 获取我的全部知识库（含文档统计）
export const getKnowledgeBases = () => request.get('/knowledge-base');

// 新建知识库
export const createKnowledgeBase = (data) => request.post('/knowledge-base', data);

// 更新知识库（重命名/改描述）
export const updateKnowledgeBase = (id, data) => request.put(`/knowledge-base/${id}`, data);

// 删除知识库（会级联删除其下全部文档）
export const deleteKnowledgeBase = (id) => request.delete(`/knowledge-base/${id}`);
