import request from '../utils/request';

export const uploadDocument = (file, knowledgeBaseId) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    params: { knowledgeBaseId },
  });
};

export const getDocuments = (knowledgeBaseId) =>
  request.get('/document', { params: { knowledgeBaseId } });

export const deleteDocument = (id) => request.delete(`/document/${id}`);

export const setDocumentEnabled = (id, enabled) =>
  request.patch(`/document/${id}/enabled`, { enabled });
