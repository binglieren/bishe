import request from '../utils/request';

export const getExams = (subject) =>
  request.get('/exam', { params: { subject } });

export const getExamDetail = (id) => request.get(`/exam/${id}`);

export const createExam = (data) => request.post('/exam', data);

export const startExam = (id) => request.post(`/exam/${id}/start`);

export const submitExam = (data) => request.post('/exam/submit', data);

export const getMyRecords = () => request.get('/exam/records');

export const getExamResult = (recordId) =>
  request.get(`/exam/record/${recordId}`);
