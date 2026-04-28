import request from './request';

export const login = (data) => request.post('/auth/login', data);

export const register = (data) => request.post('/auth/register', data);

export const getUserInfo = () => request.get('/user/info');

export const updateProfile = (data) => request.put('/user/profile', data);

// 上传/更新头像：image 为 base64 字符串（可带或不带 data: 前缀）
export const uploadAvatar = (image) =>
  request.post('/user/avatar', { image }, { timeout: 30000 });

export const checkIn = (studyMinutes) =>
  request.post(`/user/check-in?studyMinutes=${studyMinutes || 0}`);

export const getCheckInHistory = (start, end) => {
  const params = {};
  if (start) params.start = start;
  if (end) params.end = end;
  return request.get('/user/check-in/history', { params });
};