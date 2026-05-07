/**
 * useRenderedSegments —— 把消息 content 变成立即可用的 segments。
 *
 * 查询链：
 *   1. message.contentHtml（后端已缓存）
 *   2. AsyncStorage 本地缓存
 *   3. 现场 katex.renderToString（最后手段）
 *
 * 返回 { segments, ready }：
 *   - ready=false 时 segments 为占位估算（避免气泡瞬塌）
 *   - ready=true  时 segments 为最终渲染结果
 */
import { useState, useEffect, useRef } from 'react';
import { InteractionManager } from 'react-native';
import { renderLatex, hasMath } from './renderLatex';
import { getCached, setCached } from './mathCache';

/** 从 contentHtml 还原 segments（后端存储的 JSON 字符串） */
function parseContentHtml(contentHtml) {
  if (!contentHtml) return null;
  try {
    return JSON.parse(contentHtml);
  } catch {
    return null;
  }
}

/** 估算占位 segments —— 避免 WebView 加载前气泡高度塌陷为 0 */
function estimateSegments(content, fontSize, lineHeight) {
  const text = String(content || '');
  const lines = Math.max(1, Math.ceil(text.length / 30)); // 粗略行数估算
  return [
    {
      kind: 'plain',
      text,
      _estH: lines * fontSize * (lineHeight || 1.5),
    },
  ];
}

export function useRenderedSegments(content, fontSize = 15, contentHtml = null) {
  const [segments, setSegments] = useState(() =>
    estimateSegments(content, fontSize, 1.5)
  );
  const [ready, setReady] = useState(false);
  const renderRef = useRef(false);

  useEffect(() => {
    let cancelled = false;
    renderRef.current = false;

    async function resolve() {
      if (!content) {
        setSegments([]);
        setReady(true);
        return;
      }

      // 纯文本消息，不需要任何渲染
      if (!hasMath(content)) {
        setSegments([{ kind: 'plain', text: String(content) }]);
        setReady(true);
        return;
      }

      // L1: 后端 contentHtml（跨设备恢复）
      const fromBackend = parseContentHtml(contentHtml);
      if (fromBackend) {
        if (cancelled) return;
        setSegments(fromBackend);
        setReady(true);
        return;
      }

      // L2: AsyncStorage 本地缓存
      const cached = await getCached(content);
      if (cached && cached.segments) {
        if (cancelled) return;
        setSegments(cached.segments);
        setReady(true);
        return;
      }

      // L3: 现场渲染（延迟到交互空闲时，避免阻塞首屏）
      if (renderRef.current) return;
      renderRef.current = true;

      InteractionManager.runAfterInteractions(() => {
        if (cancelled) return;
        try {
          const segs = renderLatex(content);
          if (!cancelled) {
            setSegments(segs);
            setReady(true);
            // 双写缓存，后台写不阻塞 UI
            setCached(content, segs, fontSize).catch(() => {});
          }
        } catch {
          if (!cancelled) {
            setSegments([{ kind: 'plain', text: String(content) }]);
            setReady(true);
          }
        }
      });
    }

    resolve();
    return () => { cancelled = true; };
  }, [content, fontSize, contentHtml]);

  return { segments, ready };
}

/** 主动渲染并返回 segments（用于收到新消息时立刻算） */
export function prerenderMessage(content, fontSize = 15) {
  if (!content || !hasMath(content)) {
    return [{ kind: 'plain', text: String(content || '') }];
  }
  return renderLatex(content);
}
