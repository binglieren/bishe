import request from './request';

export const getMasteryOverview = () => request.get('/analysis/mastery');

export const getWeakPoints = (threshold) =>
  request.get('/analysis/weak-points', { params: { threshold } });

export const getRecommendedQuestions = (count) =>
  request.get('/analysis/recommend', { params: { count } });

export const getSubjectAnalysis = () => request.get('/analysis/subject');