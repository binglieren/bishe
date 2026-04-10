import request from './request';

export const getAiConfig = () => request.get('/ai-config');

export const saveAiConfig = (data) => request.post('/ai-config', data);

export const resetAiConfig = () => request.delete('/ai-config');