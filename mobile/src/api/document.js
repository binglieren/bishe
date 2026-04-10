import request from './request';

export const uploadDocument = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

export const getDocuments = () => request.get('/document');

export const deleteDocument = (id) => request.delete(`/document/${id}`);