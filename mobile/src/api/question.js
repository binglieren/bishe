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