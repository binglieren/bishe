import request from './request';

/**
 * 取知识图谱总数据
 * @param {string|null} subject  '数学' / '英语' / '专业课' / null（全部）
 */
export const getGraph = (subject) =>
  request.get('/knowledge-graph', { params: { subject: subject || undefined } });

/** 节点详情（点击图谱节点弹出抽屉用） */
export const getKpDetail = (kpId) =>
  request.get(`/knowledge-graph/kp/${kpId}/detail`);

/** AI 诊断报告（30s 超时，LLM 生成需要时间） */
export const getDiagnosis = (subject) =>
  request.get('/knowledge-graph/diagnosis', {
    params: { subject: subject || undefined },
    timeout: 60000,
  });

/** 收藏 / 取消收藏 */
export const focusKp = (kpId) =>
  request.post(`/knowledge-graph/kp/${kpId}/focus`);
export const unfocusKp = (kpId) =>
  request.delete(`/knowledge-graph/kp/${kpId}/focus`);

/** 取专项练习题 id */
export const getPracticeQuestionIds = (kpId, limit = 10) =>
  request.get(`/knowledge-graph/kp/${kpId}/practice`, { params: { limit } });
