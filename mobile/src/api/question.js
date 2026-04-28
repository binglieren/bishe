import request from './request';

export const getQuestions = (params) => request.get('/question', { params });

export const getQuestionDetail = (id) => request.get(`/question/${id}`);

export const createQuestion = (data) => request.post('/question', data);

export const submitAnswer = (data) => request.post('/question/submit', data);

export const getRandomQuestions = (params) =>
  request.get('/question/random', { params });

export const getWrongAnswers = (params) =>
  request.get('/question/wrong', { params });

export const resolveWrongAnswer = (id) =>
  request.put(`/question/wrong/${id}/resolve`);

export const getKnowledgePoints = (subject) =>
  request.get('/question/knowledge-points', { params: { subject } });

// ===== 用户个人题库 =====
export const getMyQuestions = () => request.get('/question/my');

export const getMyKnowledgePoints = () => request.get('/question/my/knowledge-points');

export const recordAttempt = (id, data) => request.post(`/question/${id}/attempt`, data);

export const getSimilarQuestions = (id, limit = 5) =>
  request.get(`/question/${id}/similar`, { params: { limit } });

// 个性化推荐（基于薄弱知识点 + 知识点树扩展）
export const getRecommendations = (limit = 10) =>
  request.get('/question/recommend', { params: { limit } });