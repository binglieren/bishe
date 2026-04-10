import request from '../utils/request';

// 登录（复用普通登录接口）
export const login = (data) => request.post('/auth/login', data);

// 数据统计
export const getStatistics = () => request.get('/admin/statistics');

// 用户管理
export const getUserList = (page = 0, size = 10) =>
  request.get('/admin/users', { params: { page, size } });

export const updateUserRole = (userId, role) =>
  request.put(`/admin/users/${userId}/role`, { role });

export const resetUserPassword = (userId, password) =>
  request.put(`/admin/users/${userId}/password`, { password });

export const deleteUser = (userId) =>
  request.delete(`/admin/users/${userId}`);

// 题目管理
export const getQuestionList = (params) =>
  request.get('/admin/questions', { params });

export const createQuestion = (data) =>
  request.post('/admin/questions', data);

export const updateQuestion = (questionId, data) =>
  request.put(`/admin/questions/${questionId}`, data);

export const deleteQuestion = (questionId) =>
  request.delete(`/admin/questions/${questionId}`);

// 考试管理
export const getExamList = (page = 0, size = 10) =>
  request.get('/admin/exams', { params: { page, size } });

export const createExam = (data) =>
  request.post('/admin/exams', data);

export const updateExam = (examId, data) =>
  request.put(`/admin/exams/${examId}`, data);

export const deleteExam = (examId) =>
  request.delete(`/admin/exams/${examId}`);

// 知识点管理
export const getKnowledgePointList = (page = 0, size = 20) =>
  request.get('/admin/knowledge-points', { params: { page, size } });

export const createKnowledgePoint = (data) =>
  request.post('/admin/knowledge-points', data);

export const updateKnowledgePoint = (kpId, data) =>
  request.put(`/admin/knowledge-points/${kpId}`, data);

export const deleteKnowledgePoint = (kpId) =>
  request.delete(`/admin/knowledge-points/${kpId}`);
