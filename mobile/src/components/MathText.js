/**
 * MathText —— 数学公式渲染组件（v3：预渲染 + 无 CDN + 锁定高度）
 *
 * 与 v2 的核心区别：
 *   1. 公式在消息到达时由 katex.renderToString 预渲染（纯 JS，零 CDN 请求）
 *   2. WebView 只接收预生成的静态 HTML + 本地 KaTeX CSS（不跑 JS）
 *   3. 高度首次测量后锁定，不再反复上报 → FlatList 不再因测高跳动
 *   4. 纯文字段始终走原生 <Text selectable>，不丢 selection handles
 *
 * 用法：<MathText value={content} style={...} contentHtml={htmlFromBackend?} />
 */
import React, { useMemo, useCallback, useState, useRef } from 'react';
import { View, Text, StyleSheet, Platform } from 'react-native';
import { WebView } from 'react-native-webview';
import { useRenderedSegments } from './math/useRenderedSegments';
import { KATEX_CSS } from './math/katexCss';

// ─── 纯文本段（原生 Text，保留 selectable + selection handles）───
function PlainText({ value, style }) {
  return (
    <Text
      onLongPress={() => {}}
      selectable
      style={style}
    >
      {value}
    </Text>
  );
}

// ─── 预渲染公式段（WebView 加载静态 HTML，不跑 JS）───
function MathSegmentView({ html, fontSize, color, lineHeight, initialHeight }) {
  const [height, setHeight] = useState(initialHeight);

  const colorStr = String(color || '#0F172A');

  // 静态 HTML：本地 CSS 内联注入，预渲染的 KaTeX HTML 直接嵌入
  const source = useMemo(() => {
    const htmlContent = html || '';
    return {
      html: `<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<style>${KATEX_CSS}
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:transparent}
body{font-size:${fontSize}px;color:${colorStr};line-height:${lineHeight};word-break:break-word;overflow-wrap:break-word;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;-webkit-text-size-adjust:100%}
.katex-display{margin:0.1em 0!important;overflow-x:auto;overflow-y:hidden}
.katex{font-size:1em}
.katex-error{color:#b91c1c!important;border:none!important}
</style>
</head>
<body>${htmlContent}</body>
</html>`
    };
  }, [html, fontSize, colorStr, lineHeight]);

  const handleMessage = useCallback((e) => {
    try {
      const data = JSON.parse(e.nativeEvent.data);
      if (data.type === 'height' && typeof data.value === 'number' && data.value > 0) {
        setHeight((cur) => Math.max(cur, data.value)); // 取最大值，允许换行公式撑开
      }
    } catch {}
  }, []);

  const webViewRef = useRef(null);

  const injectHeightScript = useCallback(() => {
    try {
      webViewRef.current?.injectJavaScript(`
        (function() {
          function report() {
            // 用 bounding rect 测真实可见高度，避免 KaTeX hidden MathML 撑大 scrollHeight
            var h = Math.ceil(document.body.getBoundingClientRect().height);
            if (h > 0 && window.ReactNativeWebView) {
              window.ReactNativeWebView.postMessage(JSON.stringify({type:'height',value:h}));
            }
          }
          report();
          setTimeout(report, 400);
          setTimeout(report, 1200);
          if (typeof ResizeObserver !== 'undefined') {
            try { new ResizeObserver(report).observe(document.body); } catch(e) {}
          }
        })();
        true;
      `);
    } catch {}
  }, []);

  return (
    <View style={{ width: '100%', height }}>
      <WebView
        originWhitelist={['*']}
        source={source}
        style={{ backgroundColor: 'transparent' }}
        backgroundColor="transparent"
        scrollEnabled={false}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        javaScriptEnabled
        domStorageEnabled={false}
        onShouldStartLoadWithRequest={(req) =>
          req.url === 'about:blank' || req.url.startsWith('data:')
        }
        onMessage={handleMessage}
        onLoadEnd={() => {
          // DOM 和 CSS 都就绪后注入测高脚本
          injectHeightScript();
        }}
        androidLayerType={Platform.OS === 'android' ? 'hardware' : undefined}
        ref={webViewRef}
      />
    </View>
  );
}

/**
 * MathText 主组件（v3）
 *
 * 参数：
 *   value       原始消息文本
 *   style       字体样式（fontSize / color / lineHeight）
 *   contentHtml 后端缓存的 segments JSON（可选，命中时跳过本地渲染）
 */
export function MathText({ value, style, contentHtml }) {
  if (value == null || value === '') return null;

  const flat = StyleSheet.flatten(style) || {};
  const fontSize = flat.fontSize || 15;
  const color = flat.color || '#0F172A';
  const lineHeight = flat.lineHeight ? flat.lineHeight / fontSize : 1.5;

  const { segments, ready } = useRenderedSegments(value, fontSize, contentHtml);

  // 单段纯文本 → 直接原生 Text
  if (segments.length === 1 && segments[0].kind === 'plain') {
    return <PlainText value={segments[0].text} style={style} />;
  }

  const estH = Math.ceil(fontSize * lineHeight); // 单行高度作为 plain 段兜底

  return (
    <View style={{ width: '100%' }}>
      {segments.map((seg, i) => {
        const isLast = i === segments.length - 1;
        const marginBottom = isLast ? 0 : 3;
        if (seg.kind === 'math') {
          // 公式段初始高度按内容估算，不再虚设 2x 单行高
          const charEstLines = Math.max(1, Math.ceil((seg.text || '').length / 30));
          const mathInitH = Math.ceil(charEstLines * fontSize * lineHeight + 8);
          return (
            <View key={i} style={{ marginBottom }}>
              <MathSegmentView
                html={seg.html || ''}
                fontSize={fontSize}
                color={color}
                lineHeight={lineHeight}
                initialHeight={mathInitH}
              />
            </View>
          );
        }
        return (
          <View key={i} style={{ marginBottom }}>
            <PlainText value={seg.text} style={style} />
          </View>
        );
      })}
    </View>
  );
}

export default MathText;
