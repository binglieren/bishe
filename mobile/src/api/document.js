import request from './request';

// 上传文档到指定知识库
export const uploadDocument = (knowledgeBaseId, file) => {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('knowledgeBaseId', String(knowledgeBaseId));
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// 获取某个知识库下的文档列表
export const getDocuments = (knowledgeBaseId) =>
  request.get('/document', { params: { knowledgeBaseId } });

// 切换文档在知识库中的启用状态
export const setDocumentEnabled = (id, enabled) =>
  request.patch(`/document/${id}/enabled`, { enabled });

export const deleteDocument = (id) => request.delete(`/document/${id}`);
