import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Text as RNText,
  ActivityIndicator,
  Modal,
  Image,
} from 'react-native';
import { Text, Snackbar, IconButton, Switch, Chip } from 'react-native-paper';
import { MathText } from '../components/MathText';
import { WebView } from 'react-native-webview';
import { useFocusEffect } from '@react-navigation/native';
import {
  getGraph,
  getKpDetail,
  focusKp,
  unfocusKp,
} from '../api/graph';
import { buildGraphHtml } from './graphHtml';
import { colors, radii, spacing, shadows, typography } from '../theme';

const SUBJECTS = [
  { key: null,       label: '全部', icon: '🌐' },
  { key: '数学',      label: '数学', icon: '🧮' },
  { key: '英语',      label: '英语', icon: '🔤' },
  { key: '专业课',     label: '专业课', icon: '🎓' },
];

const LEVEL_BUCKETS = [
  { key: 'weak',         label: '薄弱', color: colors.danger,  emoji: '🔴' },
  { key: 'intermediate', label: '一般', color: colors.warning, emoji: '🟡' },
  { key: 'strong',       label: '已掌握', color: colors.success, emoji: '🟢' },
  { key: 'untouched',    label: '未学', color: colors.textTertiary, emoji: '⚫' },
];

export default function KnowledgeGraphScreen({ navigation }) {
  const [subject, setSubject] = useState(null);
  const [graph, setGraph] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  // 视图层切换
  const [showHierarchy, setShowHierarchy] = useState(false);
  const [showCoOccur, setShowCoOccur] = useState(true);
  const [showUntouched, setShowUntouched] = useState(true);
  const [settingsVisible, setSettingsVisible] = useState(false);

  // 节点详情抽屉
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detail, setDetail] = useState(null);

  // WebView 引用 + 就绪状态
  const webRef = useRef(null);
  const [webReady, setWebReady] = useState(false);

  const html = useMemo(() => buildGraphHtml(), []);

  // 加载图谱数据
  const loadGraph = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getGraph(subject);
      setGraph(res.data || null);
    } catch (err) {
      setError(err?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  }, [subject]);

  // 进入页面 + 切换 subject 时重新加载
  useFocusEffect(
    useCallback(() => {
      loadGraph();
    }, [loadGraph])
  );

  // 把数据发给 WebView 渲染
  useEffect(() => {
    if (!webReady || !webRef.current) return;
    if (!graph) return;
    const payload = {
      ...graph,
      showHierarchy,
      showCoOccur,
      showUntouched,
    };
    const js = `window.renderGraph(${JSON.stringify(JSON.stringify(payload))}); true;`;
    webRef.current.injectJavaScript(js);
  }, [graph, webReady, showHierarchy, showCoOccur, showUntouched]);

  // WebView 消息处理
  const onMessage = async (e) => {
    try {
      const msg = JSON.parse(e.nativeEvent.data);
      if (msg.type === 'ready') {
        setWebReady(true);
      } else if (msg.type === 'nodeClick') {
        await openDetail(msg.id);
      } else if (msg.type === 'error') {
        setSnackMsg(msg.message || '渲染异常');
        setSnackVisible(true);
      }
    } catch {
      // ignore
    }
  };

  const openDetail = async (kpId) => {
    setDetailVisible(true);
    setDetailLoading(true);
    setDetail(null);
    try {
      const res = await getKpDetail(kpId);
      setDetail(res.data || null);
    } catch (err) {
      setSnackMsg(err?.message || '加载详情失败');
      setSnackVisible(true);
      setDetailVisible(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const toggleFocus = async () => {
    if (!detail) return;
    try {
      if (detail.focused) {
        await unfocusKp(detail.id);
        setDetail({ ...detail, focused: false });
      } else {
        await focusKp(detail.id);
        setDetail({ ...detail, focused: true });
      }
      // 刷新图谱以更新边框
      loadGraph();
    } catch (err) {
      setSnackMsg(err?.message || '操作失败');
      setSnackVisible(true);
    }
  };

  const stats = graph?.stats || { total: 0, weak: 0, intermediate: 0, strong: 0, untouched: 0 };

  return (
    <View style={styles.container}>
      {/* 顶部：科目筛选 + 设置 */}
      <View style={styles.topBar}>
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.subjectsRow}
        >
          {SUBJECTS.map((s) => {
            const active = s.key === subject;
            return (
              <TouchableOpacity
                key={String(s.key)}
                onPress={() => setSubject(s.key)}
                activeOpacity={0.7}
                style={[styles.subjectChip, active && styles.subjectChipActive]}
              >
                <RNText style={styles.subjectIcon}>{s.icon}</RNText>
                <Text style={[styles.subjectText, active && styles.subjectTextActive]}>
                  {s.label}
                </Text>
              </TouchableOpacity>
            );
          })}
        </ScrollView>
        <IconButton
          icon="cog-outline"
          size={20}
          onPress={() => setSettingsVisible(true)}
          style={styles.settingsBtn}
        />
      </View>

      {/* 中间：图谱 */}
      <View style={styles.canvas}>
        {loading && (
          <View style={styles.loadingOverlay}>
            <ActivityIndicator size="large" color={colors.primary} />
            <Text style={styles.loadingText}>加载知识图谱…</Text>
          </View>
        )}
        {error && !loading && (
          <View style={styles.loadingOverlay}>
            <RNText style={{ fontSize: 32, marginBottom: 12 }}>⚠️</RNText>
            <Text style={styles.loadingText}>{error}</Text>
            <TouchableOpacity onPress={loadGraph} style={styles.retryBtn}>
              <Text style={styles.retryText}>重试</Text>
            </TouchableOpacity>
          </View>
        )}
        <WebView
          ref={webRef}
          originWhitelist={['*']}
          source={{ html }}
          onMessage={onMessage}
          style={styles.webview}
          javaScriptEnabled
          domStorageEnabled
          startInLoadingState={false}
          androidLayerType="hardware"
          cacheEnabled
          mixedContentMode="always"
          scrollEnabled={false}
          bounces={false}
          overScrollMode="never"
        />
      </View>

      {/* 底部：统计 + 诊断按钮 */}
      <View style={styles.bottomBar}>
        <View style={styles.statsRow}>
          {LEVEL_BUCKETS.map((b) => (
            <View key={b.key} style={styles.statChip}>
              <RNText style={styles.statEmoji}>{b.emoji}</RNText>
              <Text style={styles.statLabel}>{b.label}</Text>
              <Text style={[styles.statNum, { color: b.color }]}>{stats[b.key] || 0}</Text>
            </View>
          ))}
        </View>
        <TouchableOpacity
          style={styles.diagnosisBtn}
          activeOpacity={0.85}
          onPress={() => navigation.navigate('DiagnosisReport', { subject })}
        >
          <RNText style={styles.diagnosisIcon}>🩺</RNText>
          <Text style={styles.diagnosisText}>AI 学习诊断</Text>
          <RNText style={styles.diagnosisArrow}>›</RNText>
        </TouchableOpacity>
      </View>

      {/* 设置面板 */}
      <Modal
        visible={settingsVisible}
        transparent
        animationType="fade"
        onRequestClose={() => setSettingsVisible(false)}
      >
        <TouchableOpacity
          style={styles.modalBackdrop}
          activeOpacity={1}
          onPress={() => setSettingsVisible(false)}
        >
          <View style={styles.settingsCard} onStartShouldSetResponder={() => true}>
            <View style={styles.settingsHeader}>
              <Text style={styles.settingsTitle}>视图设置</Text>
              <IconButton icon="close" size={20} onPress={() => setSettingsVisible(false)} />
            </View>

            <SettingRow
              label="共现关系（虚线）"
              hint="同题中频繁出现的两个知识点"
              value={showCoOccur}
              onChange={setShowCoOccur}
            />
            <SettingRow
              label="层级关系（实线）"
              hint="父知识点 → 子知识点的从属"
              value={showHierarchy}
              onChange={setShowHierarchy}
            />
            <SettingRow
              label="显示未学知识点"
              hint="灰色节点，关掉可使图谱更清爽"
              value={showUntouched}
              onChange={setShowUntouched}
            />
          </View>
        </TouchableOpacity>
      </Modal>

      {/* 节点详情抽屉 */}
      <Modal
        visible={detailVisible}
        transparent
        animationType="slide"
        onRequestClose={() => setDetailVisible(false)}
      >
        <TouchableOpacity
          style={styles.modalBackdrop}
          activeOpacity={1}
          onPress={() => setDetailVisible(false)}
        >
          <View style={styles.detailCard} onStartShouldSetResponder={() => true}>
            <View style={styles.detailHandle} />
            {detailLoading ? (
              <View style={{ padding: 32, alignItems: 'center' }}>
                <ActivityIndicator color={colors.primary} />
                <Text style={{ marginTop: 12, color: colors.textSecondary }}>加载中…</Text>
              </View>
            ) : detail ? (
              <DetailBody
                detail={detail}
                onToggleFocus={toggleFocus}
                onClose={() => setDetailVisible(false)}
                onPractice={() => {
                  setDetailVisible(false);
                  // 跳到 学习记录 tab 的题库页，并传 kpId 让其筛选
                  navigation.getParent()?.navigate('QuestionTab', {
                    screen: 'QuestionMain',
                    params: { kpId: detail.id, kpName: detail.name },
                  });
                }}
              />
            ) : (
              <View style={{ padding: 32, alignItems: 'center' }}>
                <Text>暂无数据</Text>
              </View>
            )}
          </View>
        </TouchableOpacity>
      </Modal>

      <Snackbar
        visible={snackVisible}
        onDismiss={() => setSnackVisible(false)}
        duration={2200}
      >
        {snackMsg}
      </Snackbar>
    </View>
  );
}

// ── 子组件 ────────────────────────────────────────────────

function SettingRow({ label, hint, value, onChange }) {
  return (
    <View style={styles.settingRow}>
      <View style={{ flex: 1 }}>
        <Text style={styles.settingLabel}>{label}</Text>
        <Text style={styles.settingHint}>{hint}</Text>
      </View>
      <Switch value={value} onValueChange={onChange} color={colors.primary} />
    </View>
  );
}

function DetailBody({ detail, onToggleFocus, onClose, onPractice }) {
  const masteryPct = detail.masteryLevel != null
    ? Math.round(detail.masteryLevel * 100)
    : null;
  const levelMeta = (() => {
    switch (detail.level) {
      case 'strong':       return { color: colors.success, label: '已掌握', emoji: '🟢' };
      case 'intermediate': return { color: colors.warning, label: '一般',   emoji: '🟡' };
      case 'weak':         return { color: colors.danger,  label: '薄弱',   emoji: '🔴' };
      default:             return { color: colors.textTertiary, label: '未学', emoji: '⚫' };
    }
  })();

  return (
    <ScrollView style={{ maxHeight: '100%' }} contentContainerStyle={{ paddingBottom: 32 }}>
      {/* 头部 */}
      <View style={styles.detailHead}>
        <View style={{ flex: 1 }}>
          <Text style={styles.detailTitle} numberOfLines={2}>{detail.name}</Text>
          <Text style={styles.detailPath} numberOfLines={1}>{detail.path}</Text>
        </View>
        <TouchableOpacity onPress={onToggleFocus} style={styles.focusBtn}>
          <RNText style={{ fontSize: 22 }}>
            {detail.focused ? '⭐' : '☆'}
          </RNText>
        </TouchableOpacity>
        <IconButton icon="close" size={22} onPress={onClose} />
      </View>

      {/* 掌握度 */}
      <View style={styles.detailSection}>
        <View style={styles.detailMasteryHead}>
          <RNText style={{ fontSize: 18, marginRight: 8 }}>{levelMeta.emoji}</RNText>
          <Text style={[styles.detailMasteryLevel, { color: levelMeta.color }]}>
            {levelMeta.label}
          </Text>
          {masteryPct != null && (
            <Text style={styles.detailMasteryPct}>· {masteryPct}%</Text>
          )}
        </View>
        <View style={styles.progressBar}>
          <View
            style={[
              styles.progressFill,
              {
                width: `${masteryPct ?? 0}%`,
                backgroundColor: levelMeta.color,
              },
            ]}
          />
        </View>
        <Text style={styles.detailStat}>
          已答 {detail.attemptedCount ?? 0} 题，答对 {detail.correctCount ?? 0} 题
        </Text>
      </View>

      {/* 错过的题 */}
      {detail.wrongQuestions?.length > 0 && (
        <View style={styles.detailSection}>
          <Text style={styles.detailSectionTitle}>
            ⚠ 错过的题（{detail.wrongQuestions.length}）
          </Text>
          {detail.wrongQuestions.map((q) => (
            <View key={q.id} style={styles.wrongItem}>
              <MathText value={`· ${q.content}`} style={styles.wrongText} />
            </View>
          ))}
        </View>
      )}

      {/* 相关知识点 */}
      {detail.relatedKps?.length > 0 && (
        <View style={styles.detailSection}>
          <Text style={styles.detailSectionTitle}>
            💡 相关知识点
          </Text>
          <View style={styles.relatedRow}>
            {detail.relatedKps.map((r) => (
              <Chip
                key={r.id}
                style={styles.relatedChip}
                textStyle={styles.relatedChipText}
                compact
              >
                {r.name} · {r.coOccurCount}
              </Chip>
            ))}
          </View>
        </View>
      )}

      {/* 操作按钮 */}
      <TouchableOpacity style={styles.practiceBtn} onPress={onPractice} activeOpacity={0.85}>
        <Text style={styles.practiceText}>专项练习这个知识点 ▶</Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

// ── 样式 ──────────────────────────────────────────────────
const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },

  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
    paddingRight: 4,
  },
  subjectsRow: {
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    gap: 8,
  },
  subjectChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: radii.pill,
    backgroundColor: colors.surfaceAlt,
    borderWidth: 1,
    borderColor: colors.border,
    marginRight: 6,
  },
  subjectChipActive: {
    backgroundColor: colors.primarySoft,
    borderColor: colors.primary,
  },
  subjectIcon: { fontSize: 14, marginRight: 4 },
  subjectText: { fontSize: 13, color: colors.textSecondary },
  subjectTextActive: { color: colors.primaryDark, fontWeight: '700' },
  settingsBtn: { margin: 0 },

  canvas: { flex: 1, position: 'relative' },
  webview: { flex: 1, backgroundColor: '#FAFAFB' },
  loadingOverlay: {
    position: 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    alignItems: 'center', justifyContent: 'center',
    backgroundColor: '#FAFAFB',
    zIndex: 10,
  },
  loadingText: { marginTop: 12, color: colors.textSecondary, fontSize: 14 },
  retryBtn: {
    marginTop: 16,
    paddingHorizontal: 20,
    paddingVertical: 8,
    backgroundColor: colors.primary,
    borderRadius: radii.pill,
  },
  retryText: { color: '#FFF', fontWeight: '600' },

  bottomBar: {
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
    paddingHorizontal: spacing.md,
    paddingTop: spacing.sm,
    paddingBottom: spacing.md,
  },
  statsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: spacing.sm,
  },
  statChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    paddingHorizontal: 8,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.md,
    gap: 4,
  },
  statEmoji: { fontSize: 12 },
  statLabel: { fontSize: 11, color: colors.textSecondary },
  statNum: { fontSize: 14, fontWeight: '700' },

  diagnosisBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    backgroundColor: colors.primary,
    borderRadius: radii.md,
    ...shadows.colored,
  },
  diagnosisIcon: { fontSize: 18, marginRight: 8 },
  diagnosisText: { color: '#FFF', fontSize: 15, fontWeight: '700', marginRight: 8 },
  diagnosisArrow: { color: '#FFF', fontSize: 22, fontWeight: '300' },

  modalBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.4)',
    justifyContent: 'flex-end',
  },

  settingsCard: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: radii.lg,
    borderTopRightRadius: radii.lg,
    padding: spacing.lg,
  },
  settingsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  settingsTitle: {
    ...typography.titleMd,
    color: colors.textPrimary,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  settingLabel: {
    fontSize: 14,
    color: colors.textPrimary,
    fontWeight: '600',
    marginBottom: 2,
  },
  settingHint: {
    fontSize: 12,
    color: colors.textTertiary,
  },

  detailCard: {
    backgroundColor: colors.surface,
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    paddingHorizontal: spacing.lg,
    paddingTop: 8,
    maxHeight: '85%',
  },
  detailHandle: {
    width: 40, height: 4, borderRadius: 2,
    backgroundColor: colors.border,
    alignSelf: 'center',
    marginBottom: 12,
  },
  detailHead: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: spacing.sm,
  },
  detailTitle: {
    ...typography.titleLg,
    color: colors.textPrimary,
  },
  detailPath: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: 2,
  },
  focusBtn: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
  },

  detailSection: {
    paddingVertical: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  detailSectionTitle: {
    fontSize: 13,
    color: colors.textSecondary,
    fontWeight: '700',
    marginBottom: 8,
  },
  detailMasteryHead: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  detailMasteryLevel: { fontSize: 16, fontWeight: '700' },
  detailMasteryPct: { fontSize: 14, color: colors.textSecondary, marginLeft: 4 },
  progressBar: {
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.surfaceAlt,
    overflow: 'hidden',
    marginBottom: 6,
  },
  progressFill: { height: '100%', borderRadius: 4 },
  detailStat: { fontSize: 12, color: colors.textTertiary },

  wrongItem: {
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  wrongText: { fontSize: 13, color: colors.textPrimary, lineHeight: 19 },

  relatedRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  relatedChip: {
    backgroundColor: colors.primarySoft,
    marginRight: 6,
    marginBottom: 6,
  },
  relatedChipText: { color: colors.primaryDark, fontSize: 12 },

  practiceBtn: {
    marginTop: spacing.lg,
    paddingVertical: 14,
    backgroundColor: colors.primary,
    borderRadius: radii.md,
    alignItems: 'center',
    ...shadows.colored,
  },
  practiceText: { color: '#FFF', fontSize: 15, fontWeight: '700' },
});
