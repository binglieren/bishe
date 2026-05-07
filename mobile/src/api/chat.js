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

/**
 * 流式消息（SSE）— 逐 token 回调。
 * @param {{ sessionId, message, image? }} data
 * @param {(token:string)=>void} onToken  每个 token 回调
 * @param {(err:Error)=>void} onError   错误回调
 * @param {()=>void} onDone            完成回调
 */
export const sendMessageStream = async (data, onToken, onError, onDone) => {
  const baseUrl = request.defaults.baseURL.replace(/\/api$/, '');
  try {
    const tok = await import('@react-native-async-storage/async-storage')
      .then(m => m.default.getItem('token')).catch(() => null);

    const res = await fetch(`${baseUrl}/api/chat/send/stream`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...data, token: tok || '' }),
    });

    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const text = await res.text();

    // 批量解析 SSE
    const lines = text.split('\n');
    let content = '';
    for (const line of lines) {
      if (line.startsWith('data:')) {
        const raw = line.slice(5).trim();
        if (!raw || raw === '{}') continue;
        try { const j = JSON.parse(raw); if (typeof j === 'string') content += j; }
        catch { content += raw; }
      }
    }
    // 模拟流式逐字输出
    for (let i = 0; i < content.length; i++) {
      onToken(content[i]);
    }
    onDone();
  } catch (err) {
    onError(err);
  }
};

        xhr.onload = () => {
          if (xhr.status >= 200 && xhr.status < 300) finishOk();
          else finishWithError(new Error(`HTTP ${xhr.status}`));
        };
        xhr.onerror = () => finishWithError(new Error('网络错误'));
        xhr.ontimeout = () => finishWithError(new Error('请求超时'));
        xhr.send(JSON.stringify(body));
      })
      .catch(e => finishWithError(e));
  });
};