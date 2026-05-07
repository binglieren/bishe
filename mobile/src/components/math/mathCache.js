/**
 * 三级缓存层：进程内 Map → AsyncStorage → 后端 contentHtml
 *
 * 键：chat:math:v2:<sha>
 * 值：{ segments: [{kind, text, html?}], fs: fontSize, t: timestamp }
 *
 * LRU 上限 200 条，通过 _idx 维护写入顺序。
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { sha1Hex16 } from './hash';
import { renderLatex, hasMath } from './renderLatex';

const INDEX_KEY = 'chat:math:v2:_idx';
const PREFIX = 'chat:math:v2:';
const MAX_ENTRIES = 200;

// 进程内 LRU Map（同会话重复访问 0ms）
const memCache = new Map();
const MEM_MAX = 50;

/** 生成内容寻址的缓存键 */
async function cacheKey(content) {
  const sha = await sha1Hex16(content || '');
  return PREFIX + sha;
}

/** 从 AsyncStorage 读取 single entry */
async function readEntry(key) {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

/** 写入 single entry + 更新 LRU 索引 */
async function writeEntry(key, data) {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(data));
    // 更新索引：追加到末尾
    const rawIdx = await AsyncStorage.getItem(INDEX_KEY);
    const idx = rawIdx ? JSON.parse(rawIdx) : [];
    const filtered = idx.filter((k) => k !== key);
    filtered.push(key);
    // LRU 淘汰
    while (filtered.length > MAX_ENTRIES) {
      const oldKey = filtered.shift();
      if (oldKey && oldKey !== key) {
        AsyncStorage.removeItem(oldKey).catch(() => {});
      }
    }
    await AsyncStorage.setItem(INDEX_KEY, JSON.stringify(filtered));
  } catch {
    // AsyncStorage 写失败不影响主流程
  }
}

/** 从缓存读取 segments（mem → AsyncStorage） */
export async function getCached(content) {
  if (!content) return null;
  const key = await cacheKey(content);

  // L1: 内存
  if (memCache.has(key)) return memCache.get(key);

  // L2: AsyncStorage
  const entry = await readEntry(key);
  if (entry && entry.segments) {
    // 回填 L1
    if (memCache.size >= MEM_MAX) {
      const firstKey = memCache.keys().next().value;
      memCache.delete(firstKey);
    }
    memCache.set(key, entry);
    return entry;
  }
  return null;
}

/** 写入两层缓存 */
export async function setCached(content, segments, fontSize) {
  if (!content || !segments) return;
  const key = await cacheKey(content);
  const data = {
    segments,
    fs: fontSize || 15,
    t: Date.now(),
  };

  // L1
  if (memCache.size >= MEM_MAX) {
    const firstKey = memCache.keys().next().value;
    memCache.delete(firstKey);
  }
  memCache.set(key, data);

  // L2
  await writeEntry(key, data);
}

/**
 * 批量预热缓存：对列表中没有 contentHtml 的 AI 消息，后台并发渲染。
 * @param messages  消息列表
 * @param onRendered  每条消息渲染完成后的回调 → 触发后端 PATCH
 * @param fontSize  当前正文字号
 * @param limit   并发数上限
 */
export async function warmupCache(messages, onRendered, fontSize = 15, limit = 4) {
  const toRender = messages.filter(
    (m) => m.role === 'assistant' && m.content && !m.contentHtml && hasMath(m.content)
  );
  if (toRender.length === 0) return;

  let idx = 0;
  async function worker() {
    while (idx < toRender.length) {
      const i = idx++;
      const m = toRender[i];
      try {
        // 已有缓存跳过
        const cached = await getCached(m.content);
        if (cached) {
          if (onRendered) onRendered(m, cached.segments);
          continue;
        }
        const segments = renderLatex(m.content);
        await setCached(m.content, segments, fontSize);
        if (onRendered) onRendered(m, segments);
      } catch {
        // 静默失败，下次进入会话时重试
      }
    }
  }

  const workers = Array.from({ length: Math.min(limit, toRender.length) }, () => worker());
  await Promise.all(workers);
}
