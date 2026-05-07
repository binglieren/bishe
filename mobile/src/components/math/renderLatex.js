/**
 * renderLatex —— 把含 LaTeX 的文本切段并预渲染为 HTML segments。
 *
 * 输入：原始消息文本（可能含 $...$、$$...$$、\(...\)、\[...\]）
 * 输出：segments[] = [{kind:'plain'|'math', text, html?}]
 *
 *   · plain 段 → React 端用原生 <Text> 渲染（保留 selection handles）
 *   · math 段 → React 端用 WebView 渲染 segment.html（已是完整 HTML 字符串）
 *
 * 关键：本函数同步运行（katex.renderToString 是同步的），单条普通消息 < 50 ms。
 *      消息接收时调用一次，结果存 AsyncStorage + 后端，永久不再重算。
 */
import katex from 'katex';

// 块级公式（独占段）：$$...$$ / \[...\]
//   split 用捕获组 → 公式串自身保留在结果数组里
const BLOCK_MATH_SPLIT = /(\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\])/;
const BLOCK_MATH_TEST = /^\s*(\$\$[\s\S]+?\$\$|\\\[[\s\S]+?\\\])\s*$/;

// 行内公式：$...$ 不跨行，\(...\) 可跨行
const INLINE_MATH = /(\$[^\$\n]+?\$|\\\([\s\S]+?\\\))/g;
const INLINE_MATH_TEST = /\$[^\$\n]+?\$|\\\([\s\S]+?\\\)/;

// 整段是否含任何公式
export const HAS_MATH = /(\$\$[\s\S]+?\$\$|\$[^\$\n]+?\$|\\\[[\s\S]+?\\\]|\\\([\s\S]+?\\\))/;

/** 转义 HTML 特殊字符（只在 plain 文本部分用） */
function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

/** 把单个公式串（含分隔符）转成 KaTeX HTML */
function renderOne(raw, displayMode) {
  let latex = raw;
  if (latex.startsWith('$$') && latex.endsWith('$$')) latex = latex.slice(2, -2);
  else if (latex.startsWith('\\[') && latex.endsWith('\\]')) latex = latex.slice(2, -2);
  else if (latex.startsWith('$') && latex.endsWith('$')) latex = latex.slice(1, -1);
  else if (latex.startsWith('\\(') && latex.endsWith('\\)')) latex = latex.slice(2, -2);
  try {
    return katex.renderToString(latex, {
      throwOnError: false,
      displayMode,
      errorColor: '#b91c1c',
      output: 'html',
    });
  } catch (e) {
    // 渲染失败时回落到等宽字符，保留原文
    return `<span class="katex-error" style="color:#b91c1c;font-family:monospace">${escapeHtml(raw)}</span>`;
  }
}

/**
 * 把"一段含行内公式的文本"渲染成 HTML：
 *   · 行内公式 → KaTeX HTML（display:false）
 *   · 文本部分 → escapeHtml 后保留
 * 段落里的换行 \n 转成 <br/>
 */
function renderInlineMixed(text) {
  // 用全局 regex 切；INLINE_MATH 带 g flag，需要先重置 lastIndex（即使刚刚不曾用，也保险起见）
  INLINE_MATH.lastIndex = 0;
  const parts = text.split(INLINE_MATH);
  let out = '';
  for (const p of parts) {
    if (!p) continue;
    if (INLINE_MATH_TEST.test(p)) {
      out += renderOne(p, false);
    } else {
      out += escapeHtml(p).replace(/\n/g, '<br/>');
    }
  }
  return out;
}

/**
 * 切段：
 *   1. 块级公式 $$...$$ / \[...\] → 独立 math 段
 *   2. 非公式文本部分按 \n\n 二次切：
 *      - 含行内公式 → math 段（混合渲染）
 *      - 纯文字 → plain 段（不渲染，React 端用原生 <Text>）
 */
export function renderLatex(content) {
  if (content == null || content === '') return [];
  const text = String(content);

  // 全文都没公式 → 单 plain 段
  if (!HAS_MATH.test(text)) {
    return [{ kind: 'plain', text }];
  }

  const segments = [];
  const parts = text.split(BLOCK_MATH_SPLIT).filter((s) => s != null && s !== '');

  for (const part of parts) {
    if (BLOCK_MATH_TEST.test(part)) {
      // 块级公式段
      segments.push({
        kind: 'math',
        text: part.trim(),
        html: renderOne(part.trim(), true),
      });
      continue;
    }
    // 非公式文本，按双换行二次切
    const paras = part.split(/\n{2,}/);
    for (const p of paras) {
      if (!p) continue;
      if (INLINE_MATH_TEST.test(p)) {
        segments.push({
          kind: 'math',
          text: p,
          html: renderInlineMixed(p),
        });
      } else {
        segments.push({ kind: 'plain', text: p });
      }
    }
  }
  return segments;
}

/** 快速判定一段文本是否需要走预渲染（节省调用 katex 的开销） */
export function hasMath(text) {
  if (text == null) return false;
  return HAS_MATH.test(String(text));
}
