import request from './request';

export const createSession = (title) =>
  request.post('/chat/session', null, { params: { title } });

export const getSessions = () => request.get('/chat/sessions');

export const getMessages = (sessionId) =>
  request.get(`/chat/session/${sessionId}/messages`);

// data: { sessionId, message, image? (base64 string) }
export const sendMessage = (data) => request.post('/chat/send', data);

export const deleteSession = (sessionId) =>
  request.delete(`/chat/session/${sessionId}`);

// 绑定/解绑会话的知识库（传 null 解绑）
export const bindSessionKnowledgeBase = (sessionId, knowledgeBaseId) =>
  request.patch(`/chat/session/${sessionId}/knowledge-base`, {
    knowledgeBaseId: knowledgeBaseId ?? null,
  });

/**
 * 语音识别：上传录音 base64，返回识别出的文字
 * @param {string} audio   录音文件 base64（不含 data: 前缀）
 * @param {string} format  扩展名，如 "m4a" / "wav" / "webm"
 * @returns { text: string }
 */
export const transcribeAudio = (audio, format = 'm4a') =>
  request.post('/chat/transcribe', { audio, format }, {
    timeout: 60000, // 语音识别可能较慢，放宽到 60 秒
  });

/**
 * 语音合成 (TTS)：把 AI 回复文本转成 WAV 音频
 * @param {string} text       要朗读的文本
 * @param {string} [voiceName]  音色名（Kore/Puck/Zephyr…），可选
 * @returns { audio: string, mimeType: 'audio/wav' }
 *   audio 为带 WAV 头的 base64，前端可直接拼成 data:audio/wav;base64,... 播放
 */
export const synthesizeSpeech = (text, voiceName) =>
  request.post('/chat/tts', { text, voiceName }, {
    timeout: 60000,
  });

/**
 * 上传预渲染 HTML（前端 KaTeX 转译完回传给后端，跨设备共享）
 * @param {number} messageId
 * @param {{ contentHtml: string, renderMeta: string }} payload
 */
export const patchMessageRender = (messageId, payload) =>
  request.patch(`/chat/message/${messageId}/render`, payload);