/**
 * ECharts 力导向图 HTML 模板（作为字符串供 WebView 加载）。
 *
 * 工作流程：
 *   1. WebView 加载本字符串 → ECharts 从 jsdelivr CDN 拉取（首次缓存后离线）
 *   2. RN 通过 webViewRef.injectJavaScript("window.renderGraph(payload)") 注入数据
 *   3. 用户点击节点 → window.ReactNativeWebView.postMessage(JSON.stringify({type:'nodeClick', id}))
 *   4. RN 在 onMessage 里收到事件，弹出详情抽屉
 *
 * 关键点：
 *   - 没有数据时显示提示语（避免空 ECharts 报错）
 *   - 颜色编码：weak=红 / intermediate=黄 / strong=绿 / untouched=灰
 *   - 节点大小按 questionCount 缩放（min 12, max 50）
 *   - hierarchy 边为浅灰实线，co_occur 边为彩色虚线（粗细按 weight）
 */
export const buildGraphHtml = () => `
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3, user-scalable=yes" />
<title>知识图谱</title>
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: #FAFAFB;
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
    overflow: hidden;
  }
  #chart { width: 100vw; height: 100vh; }
  #empty {
    position: absolute; top: 0; left: 0; right: 0; bottom: 0;
    display: none; align-items: center; justify-content: center;
    flex-direction: column; color: #888;
    text-align: center; padding: 0 24px;
  }
  #empty.show { display: flex; }
  #empty .emoji { font-size: 64px; margin-bottom: 16px; }
  #empty .title { font-size: 16px; color: #555; margin-bottom: 6px; font-weight: 600; }
  #empty .sub { font-size: 13px; color: #999; line-height: 1.5; }
  #loading {
    position: absolute; top: 0; left: 0; right: 0; bottom: 0;
    display: flex; align-items: center; justify-content: center;
    background: #FAFAFB; color: #888; font-size: 14px;
  }
  #loading.hide { display: none; }
  .spinner {
    width: 28px; height: 28px;
    border: 3px solid #E0E0E0;
    border-top-color: #4F46E5;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
    margin-bottom: 12px;
  }
  #loading-wrap { display: flex; flex-direction: column; align-items: center; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>

<div id="chart"></div>
<div id="loading"><div id="loading-wrap"><div class="spinner"></div><div id="loading-text">加载图谱中…</div></div></div>
<div id="empty">
  <div class="emoji">🌱</div>
  <div class="title">还没有可显示的知识点</div>
  <div class="sub">先去做几道题，或换一个科目试试</div>
</div>

<script>
  // ── 多 CDN 容错加载 ECharts ───────────────────────────
  // 国内 CDN 优先，jsdelivr 兜底；任何一个成功就开始 init
  // 全部失败时把错误抛到 RN 控制台并显示明显提示
  const ECHARTS_CDNS = [
    'https://cdn.staticfile.org/echarts/5.5.0/echarts.min.js',     // 七牛云
    'https://lib.baomitu.com/echarts/5.5.0/echarts.min.js',        // 360
    'https://unpkg.com/echarts@5.5.0/dist/echarts.min.js',          // unpkg
    'https://cdn.jsdelivr.net/npm/echarts@5.5.0/dist/echarts.min.js'
  ];

  function postMsg(obj) {
    if (window.ReactNativeWebView && window.ReactNativeWebView.postMessage) {
      window.ReactNativeWebView.postMessage(JSON.stringify(obj));
    }
  }

  let chart = null;
  let pending = null;
  let echartsReady = false;

  function loadOneCdn(idx) {
    if (idx >= ECHARTS_CDNS.length) {
      const t = document.getElementById('loading-text');
      if (t) t.textContent = 'ECharts 加载失败：请检查网络';
      postMsg({ type: 'error', message: 'echarts 全部 CDN 加载失败，请检查 WebView 网络' });
      return;
    }
    const url = ECHARTS_CDNS[idx];
    const s = document.createElement('script');
    s.src = url;
    s.async = false;
    s.onload = () => {
      if (typeof echarts === 'undefined') {
        // 加载完但 global 没生成（极罕见），换下一家
        loadOneCdn(idx + 1);
      } else {
        echartsReady = true;
        postMsg({ type: 'echartsLoaded', from: url });
        // 通知 RN 可以注入数据了（务必在 echartsReady 置 true 之后）
        postMsg({ type: 'ready' });
        // 如果 RN 已经先注入了数据，pending 会被存下，这里取出补渲染
        if (pending) {
          const p = pending; pending = null;
          try { _doRender(p); }
          catch (e) { postMsg({ type: 'error', message: 'pending render failed: ' + (e && e.message ? e.message : e) }); }
        }
      }
    };
    s.onerror = () => loadOneCdn(idx + 1);
    document.head.appendChild(s);
  }

  loadOneCdn(0);
</script>
<script>

  function levelColor(level) {
    switch (level) {
      case 'strong':       return '#10B981'; // 绿
      case 'intermediate': return '#F59E0B'; // 黄
      case 'weak':         return '#EF4444'; // 红
      default:             return '#9CA3AF'; // 灰 (untouched)
    }
  }

  function levelToZh(level) {
    return ({
      strong: '已掌握', intermediate: '一般',
      weak: '薄弱', untouched: '未学'
    })[level] || '未知';
  }

  // size scaling: questionCount 1 → 12; 30+ → 50
  function nodeSize(qc) {
    if (!qc || qc < 1) return 12;
    return Math.min(50, 12 + Math.sqrt(qc) * 6);
  }

  // 内层渲染函数，独立于 window.renderGraph 避免命名冲突导致无限递归
  function _doRender(payload) {
    if (typeof echarts === 'undefined') {
      postMsg({ type: 'error', message: 'echarts 还没加载完，请检查网络后重试' });
      return;
    }
    if (!chart) {
      chart = echarts.init(document.getElementById('chart'), null, { renderer: 'canvas' });
      chart.on('click', function (params) {
        if (params.dataType === 'node' && params.data && params.data.id != null) {
          postMsg({ type: 'nodeClick', id: params.data.id, name: params.data.name });
        }
      });

      // 缩放时动态调整 label 字体大小
      let currentZoom = 1;
      let rafId = null;
      chart.on('graphroam', function (params) {
        if (params.zoom == null || params.zoom === currentZoom) return;
        currentZoom = params.zoom;
        if (rafId) cancelAnimationFrame(rafId);
        rafId = requestAnimationFrame(function () {
          const fs = Math.round(Math.max(6, Math.min(22, 11 * currentZoom)));
          chart.setOption({ series: [{ label: { fontSize: fs } }] });
        });
      });

      window.addEventListener('resize', () => chart && chart.resize());
    }

    document.getElementById('loading').classList.add('hide');

    if (!payload || !payload.nodes || payload.nodes.length === 0) {
      document.getElementById('empty').classList.add('show');
      chart.clear();
      return;
    }
    document.getElementById('empty').classList.remove('show');

    const showHierarchy = payload.showHierarchy !== false;
    const showCoOccur   = payload.showCoOccur   !== false;
    const showUntouched = payload.showUntouched !== false;

    let nodes = payload.nodes;
    if (!showUntouched) {
      nodes = nodes.filter(n => n.level !== 'untouched');
    }
    const visibleIds = new Set(nodes.map(n => n.id));

    const echartsNodes = nodes.map(n => ({
      id: String(n.id),
      name: n.name,
      symbolSize: nodeSize(n.questionCount),
      itemStyle: {
        color: levelColor(n.level),
        borderColor: n.focused ? '#4F46E5' : 'transparent',
        borderWidth: n.focused ? 3 : 0,
        shadowBlur: n.level === 'weak' ? 10 : 0,
        shadowColor: n.level === 'weak' ? 'rgba(239,68,68,0.5)' : 'transparent',
      },
      label: {
        show: true,
        position: 'right',
        fontSize: 11,
        color: '#374151',
      },
      data: { id: n.id, name: n.name, level: n.level }
    }));

    const echartsEdges = (payload.edges || [])
      .filter(e => visibleIds.has(e.source) && visibleIds.has(e.target))
      .filter(e => (e.type === 'hierarchy' && showHierarchy) ||
                   (e.type === 'co_occur' && showCoOccur))
      .map(e => ({
        source: String(e.source),
        target: String(e.target),
        lineStyle: e.type === 'hierarchy'
          ? { color: '#CBD5E1', width: 1, type: 'solid', opacity: 0.6 }
          : {
              color: '#A78BFA',
              width: Math.min(3, Math.max(0.6, (e.weight || 1) / 3)),
              type: 'dashed',
              opacity: 0.5,
              curveness: 0.15,
            }
      }));

    chart.setOption({
      backgroundColor: '#FAFAFB',
      tooltip: {
        formatter: (params) => {
          if (params.dataType !== 'node') return '';
          const d = params.data || {};
          return '<b>' + (d.name || '') + '</b><br/>' +
                 '掌握度：' + levelToZh(d.level);
        }
      },
      series: [{
        type: 'graph',
        layout: 'force',
        roam: true,
        draggable: true,
        zoom: 1,
        scaleLimit: { min: 0.3, max: 5 },
        symbolSize: 24,
        edgeSymbol: ['none', 'none'],
        force: {
          repulsion: 120,
          edgeLength: [100, 220],
          gravity: 0.12,
          friction: 0.7,
        },
        emphasis: {
          focus: 'adjacency',
          scale: 1.1,
          label: { fontWeight: 'bold' }
        },
        animationDurationUpdate: 200,
        data: echartsNodes,
        links: echartsEdges,
      }]
    }, true);

    setTimeout(() => chart && chart.resize(), 100);
  }

  // 暴露给 RN 注入调用
  window.renderGraph = function (payloadJson) {
    try {
      const payload = typeof payloadJson === 'string' ? JSON.parse(payloadJson) : payloadJson;
      // ECharts 还没加载完时先存起来，等 loader onload 后会自动补渲染
      if (!echartsReady) {
        pending = payload;
        return;
      }
      _doRender(payload);
    } catch (e) {
      postMsg({ type: 'error', message: 'render failed: ' + (e && e.message ? e.message : e) });
    }
  };

  // RN 端可调用 window.refit() 让图重新自适应
  window.refit = function () {
    if (chart) chart.resize();
  };

  // 注：'ready' 不再在这里发，由 ECharts loader onload 后统一发送

</script>
</body>
</html>
`;
