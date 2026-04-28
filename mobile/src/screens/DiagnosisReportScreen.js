import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  ActivityIndicator,
  TouchableOpacity,
  Text as RNText,
} from 'react-native';
import { Text, Snackbar, IconButton } from 'react-native-paper';
import { getDiagnosis } from '../api/graph';
import { colors, radii, spacing, shadows, typography } from '../theme';

const SUBJECT_LABEL = {
  数学: '数学',
  英语: '英语',
  专业课: '专业课',
};

export default function DiagnosisReportScreen({ route, navigation }) {
  const subject = route?.params?.subject || null;
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getDiagnosis(subject);
      setReport(res.data || null);
    } catch (err) {
      setError(err?.message || '加载诊断失败');
    } finally {
      setLoading(false);
    }
  }, [subject]);

  useEffect(() => { load(); }, [load]);

  const subjectLabel = subject ? SUBJECT_LABEL[subject] || subject : '全部科目';

  if (loading) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text style={styles.loadingText}>AI 正在分析你的学习数据…</Text>
        <Text style={styles.loadingHint}>这可能需要 10-30 秒</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View style={styles.center}>
        <RNText style={{ fontSize: 48, marginBottom: 12 }}>⚠️</RNText>
        <Text style={{ color: colors.textSecondary }}>{error}</Text>
        <TouchableOpacity onPress={load} style={styles.retryBtn}>
          <Text style={styles.retryText}>重试</Text>
        </TouchableOpacity>
      </View>
    );
  }

  if (!report) return null;

  const progressPct = report.totalCount > 0
    ? Math.round((report.attemptedCount / report.totalCount) * 100)
    : 0;

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <ScrollView contentContainerStyle={styles.container}>
        {/* 顶部 hero */}
        <View style={styles.hero}>
          <View style={styles.heroTop}>
            <RNText style={styles.heroIcon}>🩺</RNText>
            <View style={{ flex: 1 }}>
              <Text style={styles.heroTitle}>学习诊断 · {subjectLabel}</Text>
              {report.examDays != null && (
                <Text style={styles.heroSub}>距离考试 {report.examDays} 天</Text>
              )}
            </View>
          </View>

          {/* 整体进度条 */}
          <View style={styles.progressWrap}>
            <View style={styles.progressBar}>
              <View
                style={[
                  styles.progressFill,
                  { width: `${progressPct}%` },
                ]}
              />
            </View>
            <Text style={styles.progressText}>
              已练习 {report.attemptedCount}/{report.totalCount} 个知识点（{progressPct}%）
            </Text>
          </View>
        </View>

        {/* AI 建议 */}
        <View style={[styles.card, styles.adviceCard]}>
          <View style={styles.adviceHead}>
            <RNText style={styles.adviceIcon}>✨</RNText>
            <Text style={styles.adviceTitle}>AI 学习建议</Text>
          </View>
          <Text style={styles.adviceBody}>
            {report.aiAdvice || '（暂无建议）'}
          </Text>
        </View>

        {/* 急需补强 */}
        <Section
          title="🚨 急需补强"
          subtitle={`mastery < 50% 且已答 > 3 题（${report.weakKps?.length || 0}）`}
          color={colors.danger}
          items={report.weakKps}
          emptyText="🎉 暂无明显薄弱点，继续保持！"
          renderItem={(n) => (
            <KpRow key={n.id} kp={n} accent={colors.danger} />
          )}
        />

        {/* 还没接触 */}
        <Section
          title="🌱 还没接触"
          subtitle={`重要但未练习的高频考点（top ${report.untouchedKps?.length || 0}）`}
          color={colors.info}
          items={report.untouchedKps}
          emptyText="✅ 你已覆盖全部知识点"
          renderItem={(n) => (
            <KpRow key={n.id} kp={n} accent={colors.info} showCount />
          )}
        />

        {/* 已掌握 */}
        <Section
          title="✅ 已经掌握"
          subtitle={`mastery ≥ 80%（${report.strongKps?.length || 0}）`}
          color={colors.success}
          items={report.strongKps}
          emptyText="加油，还没有完全掌握的知识点"
          renderItem={(n) => (
            <KpRow key={n.id} kp={n} accent={colors.success} />
          )}
        />

        <TouchableOpacity
          style={styles.refreshBtn}
          onPress={load}
          activeOpacity={0.85}
        >
          <RNText style={{ fontSize: 16, marginRight: 6 }}>🔄</RNText>
          <Text style={styles.refreshText}>重新生成诊断</Text>
        </TouchableOpacity>
      </ScrollView>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

function Section({ title, subtitle, color, items, emptyText, renderItem }) {
  const empty = !items || items.length === 0;
  return (
    <View style={[styles.card, { borderLeftColor: color, borderLeftWidth: 4 }]}>
      <Text style={styles.sectionTitle}>{title}</Text>
      <Text style={styles.sectionSub}>{subtitle}</Text>
      {empty ? (
        <Text style={styles.emptyHint}>{emptyText}</Text>
      ) : (
        <View style={{ marginTop: 8 }}>
          {items.map((n) => renderItem(n))}
        </View>
      )}
    </View>
  );
}

function KpRow({ kp, accent, showCount = false }) {
  const pct = kp.masteryLevel != null ? Math.round(kp.masteryLevel * 100) : null;
  return (
    <View style={styles.kpRow}>
      <View style={[styles.kpDot, { backgroundColor: accent }]} />
      <View style={{ flex: 1 }}>
        <Text style={styles.kpName} numberOfLines={1}>{kp.name}</Text>
        <Text style={styles.kpMeta}>
          {showCount
            ? `关联 ${kp.questionCount || 0} 题`
            : `掌握度 ${pct ?? '—'}% · 已答 ${kp.attemptedCount || 0}`}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1, alignItems: 'center', justifyContent: 'center',
    backgroundColor: colors.background, padding: spacing.xl,
  },
  loadingText: { marginTop: 16, color: colors.textPrimary, fontSize: 15 },
  loadingHint: { marginTop: 6, color: colors.textTertiary, fontSize: 12 },
  retryBtn: {
    marginTop: 20, paddingHorizontal: 24, paddingVertical: 10,
    backgroundColor: colors.primary, borderRadius: radii.pill,
  },
  retryText: { color: '#FFF', fontWeight: '600' },

  container: { padding: spacing.md, paddingBottom: spacing['2xl'] },

  hero: {
    backgroundColor: colors.primary,
    borderRadius: radii.lg,
    padding: spacing.lg,
    marginBottom: spacing.md,
    ...shadows.colored,
  },
  heroTop: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  heroIcon: { fontSize: 32, marginRight: 12 },
  heroTitle: { color: '#FFF', fontSize: 18, fontWeight: '700' },
  heroSub: { color: 'rgba(255,255,255,0.85)', fontSize: 13, marginTop: 2 },

  progressWrap: { marginTop: 4 },
  progressBar: {
    height: 8,
    borderRadius: 4,
    backgroundColor: 'rgba(255,255,255,0.25)',
    overflow: 'hidden',
    marginBottom: 6,
  },
  progressFill: {
    height: '100%',
    borderRadius: 4,
    backgroundColor: '#FFF',
  },
  progressText: { color: '#FFF', fontSize: 12 },

  card: {
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: spacing.md,
    marginBottom: spacing.md,
    ...shadows.card,
  },
  adviceCard: {
    backgroundColor: colors.primarySoft,
  },
  adviceHead: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  adviceIcon: { fontSize: 18, marginRight: 8 },
  adviceTitle: { fontSize: 15, fontWeight: '700', color: colors.primaryDark },
  adviceBody: {
    fontSize: 14,
    color: colors.textPrimary,
    lineHeight: 22,
  },

  sectionTitle: { fontSize: 15, fontWeight: '700', color: colors.textPrimary },
  sectionSub: { fontSize: 12, color: colors.textTertiary, marginTop: 2 },
  emptyHint: { marginTop: 10, fontSize: 13, color: colors.textTertiary },

  kpRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  kpDot: {
    width: 8, height: 8, borderRadius: 4,
    marginRight: 10,
  },
  kpName: { fontSize: 14, color: colors.textPrimary, fontWeight: '600' },
  kpMeta: { fontSize: 11, color: colors.textTertiary, marginTop: 2 },

  refreshBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 12,
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    marginTop: spacing.sm,
  },
  refreshText: { fontSize: 14, color: colors.textSecondary, fontWeight: '600' },
});
