import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  StyleSheet,
  FlatList,
  ScrollView,
  TouchableOpacity,
  Text as RNText,
} from 'react-native';
import {
  Text,
  Button,
  Snackbar,
  SegmentedButtons,
  ActivityIndicator,
} from 'react-native-paper';
import dayjs from 'dayjs';
import {
  getMyQuestions,
  getMyKnowledgePoints,
  recordAttempt,
  getSimilarQuestions,
  getRecommendations,
} from '../api/question';
import { colors, radii, spacing, shadows, typography } from '../theme';
import ModernCard from '../components/ModernCard';
import { MathText } from '../components/MathText';

const TYPE_COLORS = {
  单选: colors.single,
  多选: colors.multi,
  填空: colors.blank,
  简答: colors.short,
  证明: colors.proof,
};

// 题库分类（保存时已在后端归一化为三类）
const SUBJECT_CATEGORIES = [
  { value: 'all',  label: '全部',   icon: '📚' },
  { value: '数学', label: '数学',   icon: '🧮' },
  { value: '英语', label: '英语',   icon: '🔤' },
  { value: '专业课', label: '专业课', icon: '🎓' },
];

// 把可能存在的旧数据（如"政治"）映射到当前 3 类，保持筛选一致性
const normalizeSubjectForFilter = (subject) => {
  if (!subject) return '专业课';
  const s = String(subject).trim();
  if (s.includes('数学')) return '数学';
  if (s.includes('英语')) return '英语';
  return '专业课';
};

const CATEGORY_META = {
  weak:       { label: '薄弱点',   color: colors.weak,      bg: colors.dangerSoft,  icon: '⚠️' },
  sibling:    { label: '相关点',   color: colors.sibling,   bg: colors.warningSoft, icon: '🔗' },
  related:    { label: '延伸点',   color: colors.related,   bg: colors.infoSoft,    icon: '📎' },
  revisit:    { label: '错题回顾', color: colors.revisit,   bg: '#F3E8FF',          icon: '🔁' },
  cold_start: { label: '入门推荐', color: colors.coldStart, bg: colors.successSoft, icon: '🌱' },
};

export default function QuestionScreen() {
  const [activeTab, setActiveTab] = useState('bank');
  const [subjectFilter, setSubjectFilter] = useState('all');

  const [myQuestions, setMyQuestions] = useState([]);
  const [loadingBank, setLoadingBank] = useState(false);

  const [knowledgePoints, setKnowledgePoints] = useState([]);
  const [loadingKp, setLoadingKp] = useState(false);

  const [recommendations, setRecommendations] = useState([]);
  const [loadingRec, setLoadingRec] = useState(false);

  const [practiceItem, setPracticeItem] = useState(null);
  const [selectedOption, setSelectedOption] = useState('');
  const [selectedOptions, setSelectedOptions] = useState([]);
  const [showAnswer, setShowAnswer] = useState(false);
  const [attemptResult, setAttemptResult] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const [similarQuestions, setSimilarQuestions] = useState([]);
  const [loadingSimilar, setLoadingSimilar] = useState(false);

  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  // ── Data loading ─────────────────────────────────────
  const loadMyQuestions = useCallback(async () => {
    setLoadingBank(true);
    try {
      const res = await getMyQuestions();
      setMyQuestions(res.data || []);
    } catch (err) {
      setSnackMsg('加载题库失败');
      setSnackVisible(true);
    } finally {
      setLoadingBank(false);
    }
  }, []);

  const loadKnowledgePoints = useCallback(async () => {
    setLoadingKp(true);
    try {
      const res = await getMyKnowledgePoints();
      setKnowledgePoints(res.data || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingKp(false);
    }
  }, []);

  const loadRecommendations = useCallback(async () => {
    setLoadingRec(true);
    try {
      const res = await getRecommendations(10);
      setRecommendations(res.data || []);
    } catch (err) {
      setSnackMsg('加载推荐失败');
      setSnackVisible(true);
    } finally {
      setLoadingRec(false);
    }
  }, []);

  useEffect(() => { loadMyQuestions(); }, []);
  useEffect(() => {
    if (activeTab === 'kp') loadKnowledgePoints();
    else if (activeTab === 'rec') loadRecommendations();
  }, [activeTab]);

  // ── Practice modal ───────────────────────────────────
  const openPractice = (item) => {
    setPracticeItem(item);
    setSelectedOption('');
    setSelectedOptions([]);
    setShowAnswer(false);
    setAttemptResult(null);
    setSimilarQuestions([]);
  };

  const closePractice = () => {
    setPracticeItem(null);
    loadMyQuestions();
  };

  const handleSubmit = async () => {
    const q = practiceItem?.question;
    if (!q) return;

    let userAnswer = '';
    if (q.type === '单选') {
      if (!selectedOption) { setSnackMsg('请选择一个选项'); setSnackVisible(true); return; }
      userAnswer = selectedOption;
    } else if (q.type === '多选') {
      if (selectedOptions.length === 0) { setSnackMsg('请至少选择一项'); setSnackVisible(true); return; }
      userAnswer = [...selectedOptions].sort().join('');
    }

    setSubmitting(true);
    try {
      const res = await recordAttempt(q.id, { userAnswer });
      setAttemptResult(res.data);
      loadSimilar(q.id);
    } catch (err) {
      setSnackMsg('提交失败，请重试');
      setSnackVisible(true);
    } finally {
      setSubmitting(false);
    }
  };

  const loadSimilar = async (questionId) => {
    setLoadingSimilar(true);
    try {
      const res = await getSimilarQuestions(questionId, 3);
      setSimilarQuestions(res.data || []);
    } catch (err) {
      // silent
    } finally {
      setLoadingSimilar(false);
    }
  };

  const toggleOption = (label) => {
    setSelectedOptions(prev =>
      prev.includes(label) ? prev.filter(l => l !== label) : [...prev, label]
    );
  };

  const getAccuracy = (uq) => {
    if (!uq.totalAttempts) return null;
    return Math.round((uq.correctCount / uq.totalAttempts) * 100);
  };

  const getLastTime = (uq) => {
    if (!uq.lastAttemptAt) return null;
    return dayjs(uq.lastAttemptAt).format('MM-DD HH:mm');
  };

  const getOptionStyle = (label, isCorrectOpt) => {
    if (!attemptResult) return null;
    if (isCorrectOpt) return styles.optionCorrect;
    const q = practiceItem?.question;
    const userSelected =
      q?.type === '单选' ? selectedOption === label : selectedOptions.includes(label);
    if (userSelected && !isCorrectOpt) return styles.optionWrong;
    return null;
  };

  // ── Pill components ──────────────────────────────────
  const Pill = ({ children, bg, fg, mode = 'solid' }) => (
    <View
      style={[
        styles.pill,
        mode === 'solid'
          ? { backgroundColor: bg }
          : { backgroundColor: 'transparent', borderWidth: 1, borderColor: bg },
      ]}
    >
      <Text style={[styles.pillText, { color: fg || '#FFFFFF' }]}>{children}</Text>
    </View>
  );

  // ── Bank card ────────────────────────────────────────
  const renderQuestionCard = ({ item }) => {
    const q = item.question;
    if (!q) return null;
    const acc = getAccuracy(item);
    const lastTime = getLastTime(item);
    const typeColor = TYPE_COLORS[q.type] || colors.primary;

    return (
      <ModernCard style={styles.card} onPress={() => openPractice(item)} padding={14}>
        <View style={styles.pillRow}>
          <Pill bg={typeColor}>{q.type}</Pill>
          <Pill bg={colors.border} fg={colors.textSecondary} mode="outline">{q.subject}</Pill>
          {acc != null ? (
            <Pill
              bg={acc >= 70 ? colors.successSoft : acc >= 40 ? colors.warningSoft : colors.dangerSoft}
              fg={acc >= 70 ? '#065F46' : acc >= 40 ? '#92400E' : '#991B1B'}
            >
              正确率 {acc}%
            </Pill>
          ) : (
            <Pill bg={colors.surfaceAlt} fg={colors.textTertiary}>未做过</Pill>
          )}
        </View>

        <Text style={styles.content} numberOfLines={2}>{q.content}</Text>

        <View style={styles.cardFooter}>
          {lastTime ? (
            <Text style={styles.meta}>🕒 上次 {lastTime}</Text>
          ) : (
            <Text style={styles.meta}>待开始</Text>
          )}
          <Text style={styles.footerCta}>做题 →</Text>
        </View>
      </ModernCard>
    );
  };

  // ── Recommendation card ──────────────────────────────
  const renderRecommendationCard = ({ item }) => {
    const q = item.question;
    if (!q) return null;
    const meta = CATEGORY_META[item.category] || CATEGORY_META.cold_start;
    const typeColor = TYPE_COLORS[q.type] || colors.primary;

    const practiceWrapper = {
      id: `rec-${q.id}`,
      question: q,
      totalAttempts: 0,
      correctCount: 0,
      lastAttemptAt: null,
    };

    return (
      <ModernCard
        style={styles.card}
        accent={meta.color}
        padding={14}
        onPress={() => openPractice(practiceWrapper)}
      >
        <View style={styles.pillRow}>
          <View style={[styles.categoryBadge, { backgroundColor: meta.bg }]}>
            <RNText style={{ fontSize: 12 }}>{meta.icon}</RNText>
            <Text style={[styles.categoryBadgeText, { color: meta.color }]}>{meta.label}</Text>
          </View>
          <Pill bg={typeColor}>{q.type}</Pill>
          <Pill bg={colors.border} fg={colors.textSecondary} mode="outline">{q.subject}</Pill>
          <Pill bg={colors.border} fg={colors.textTertiary} mode="outline">难度 {q.difficulty || 3}</Pill>
        </View>

        <Text style={[styles.reasonText, { color: meta.color }]}>💡 {item.reason}</Text>
        <Text style={styles.content} numberOfLines={2}>{q.content}</Text>

        <View style={styles.cardFooter}>
          <Text style={styles.meta}>AI 精准推荐</Text>
          <Text style={[styles.footerCta, { color: meta.color }]}>立即练习 →</Text>
        </View>
      </ModernCard>
    );
  };

  // ── Knowledge point card ─────────────────────────────
  const renderKpCard = ({ item }) => (
    <ModernCard style={styles.kpCard} padding={14}>
      <View style={styles.kpRow}>
        <View style={styles.kpIconWrap}>
          <RNText style={{ fontSize: 18 }}>📘</RNText>
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.kpName}>{item.name}</Text>
          <Text style={styles.kpSubject}>{item.subject}</Text>
        </View>
      </View>
    </ModernCard>
  );

  // ── Practice Modal ───────────────────────────────────
  const renderPracticeModal = () => {
    if (!practiceItem) return null;
    const q = practiceItem.question;
    const typeColor = TYPE_COLORS[q.type] || colors.primary;

    return (
      <View style={styles.modalOverlay}>
        <ScrollView
          style={styles.modal}
          contentContainerStyle={styles.modalContent}
          showsVerticalScrollIndicator={false}
        >
          {/* Header */}
          <View style={styles.modalHeader}>
            <View style={styles.pillRow}>
              <Pill bg={typeColor}>{q.type}</Pill>
              <Pill bg={colors.border} fg={colors.textSecondary} mode="outline">{q.subject}</Pill>
            </View>
            <TouchableOpacity onPress={closePractice} style={styles.closeIcon}>
              <RNText style={{ fontSize: 20, color: colors.textSecondary }}>✕</RNText>
            </TouchableOpacity>
          </View>

          {/* Question */}
          <ModernCard padding={18} elevation="sm" style={{ marginBottom: spacing.md }}>
            <Text style={styles.questionLabel}>📝 题目</Text>
            <MathText value={q.content} style={styles.questionText} />
          </ModernCard>

          {/* Options */}
          {(q.type === '单选' || q.type === '多选') && q.options?.length > 0 && (
            <View style={styles.optionsContainer}>
              {q.options.map((opt) => {
                const isSelected =
                  q.type === '单选' ? selectedOption === opt.label : selectedOptions.includes(opt.label);
                const overrideStyle = getOptionStyle(opt.label, opt.isCorrect);

                return (
                  <TouchableOpacity
                    key={opt.label}
                    style={[
                      styles.optionItem,
                      isSelected && !attemptResult && styles.optionSelected,
                      overrideStyle,
                    ]}
                    onPress={() => {
                      if (attemptResult) return;
                      if (q.type === '单选') setSelectedOption(opt.label);
                      else toggleOption(opt.label);
                    }}
                    disabled={!!attemptResult}
                    activeOpacity={0.8}
                  >
                    <View
                      style={[
                        styles.optionLabelBox,
                        isSelected && !attemptResult && { backgroundColor: colors.primary },
                        overrideStyle === styles.optionCorrect && { backgroundColor: colors.success },
                        overrideStyle === styles.optionWrong && { backgroundColor: colors.danger },
                      ]}
                    >
                      <Text style={styles.optionLabel}>{opt.label}</Text>
                    </View>
                    <MathText value={opt.content} style={styles.optionContent} />
                    {attemptResult && opt.isCorrect && (
                      <Text style={styles.optionMark}>✓</Text>
                    )}
                    {attemptResult && isSelected && !opt.isCorrect && (
                      <Text style={[styles.optionMark, { color: colors.danger }]}>✗</Text>
                    )}
                  </TouchableOpacity>
                );
              })}
            </View>
          )}

          {/* 简答/填空/证明 */}
          {(q.type === '简答' || q.type === '填空' || q.type === '证明') && (
            <View>
              {!showAnswer ? (
                <Button
                  mode="contained-tonal"
                  onPress={() => {
                    setShowAnswer(true);
                    recordAttempt(q.id, { userAnswer: '__viewed__' }).catch(() => {});
                    loadSimilar(q.id);
                  }}
                  style={styles.showAnswerBtn}
                  icon="eye"
                >
                  查看参考答案
                </Button>
              ) : (
                <ModernCard
                  padding={16}
                  style={{ backgroundColor: colors.successSoft, marginTop: spacing.sm }}
                >
                  <Text style={styles.answerLabel}>✅ 参考答案</Text>
                  <MathText value={q.answer} style={styles.answerText} />
                  {q.analysis ? (
                    <>
                      <Text style={[styles.answerLabel, { marginTop: spacing.md }]}>📖 解析</Text>
                      <MathText value={q.analysis} style={styles.analysisText} />
                    </>
                  ) : null}
                </ModernCard>
              )}
            </View>
          )}

          {/* Submit */}
          {(q.type === '单选' || q.type === '多选') && !attemptResult && (
            <Button
              mode="contained"
              onPress={handleSubmit}
              loading={submitting}
              style={styles.submitBtn}
              contentStyle={{ paddingVertical: 4 }}
              labelStyle={{ fontSize: 15, fontWeight: '700' }}
            >
              提交答案
            </Button>
          )}

          {/* Result */}
          {attemptResult && (
            <ModernCard
              padding={16}
              style={[
                { marginTop: spacing.md },
                { backgroundColor: attemptResult.isCorrect ? colors.successSoft : colors.dangerSoft },
              ]}
            >
              <Text
                style={[
                  styles.resultTitle,
                  { color: attemptResult.isCorrect ? colors.success : colors.danger },
                ]}
              >
                {attemptResult.isCorrect ? '🎉 回答正确！' : '💪 再接再厉'}
              </Text>
              {!attemptResult.isCorrect && (
                <Text style={styles.resultAnswer}>
                  正确答案：<Text style={{ fontWeight: '700' }}>{attemptResult.correctAnswer}</Text>
                </Text>
              )}
              {attemptResult.analysis ? (
                <MathText value={"📖 " + attemptResult.analysis} style={styles.analysisText} />
              ) : null}
            </ModernCard>
          )}

          {/* Similar */}
          {(attemptResult || showAnswer) && (
            <View style={styles.similarSection}>
              <Text style={styles.similarTitle}>🔍 推荐相似题目</Text>
              {loadingSimilar ? (
                <ActivityIndicator size="small" color={colors.primary} />
              ) : similarQuestions.length > 0 ? (
                similarQuestions.map((sq) => (
                  <ModernCard
                    key={sq.id}
                    padding={12}
                    elevation="sm"
                    style={styles.similarCard}
                  >
                    <View style={styles.pillRow}>
                      <Pill bg={TYPE_COLORS[sq.type] || colors.primary}>{sq.type}</Pill>
                    </View>
                    <Text numberOfLines={2} style={styles.similarContent}>{sq.content}</Text>
                  </ModernCard>
                ))
              ) : (
                <Text style={styles.noSimilar}>暂无相似题目</Text>
              )}
            </View>
          )}

          <Button
            mode="outlined"
            onPress={closePractice}
            style={styles.closeBtn}
            textColor={colors.textSecondary}
          >
            关闭
          </Button>
        </ScrollView>
      </View>
    );
  };

  // ── Empty state ──────────────────────────────────────
  const EmptyState = ({ icon, title, hint, children }) => (
    <View style={styles.empty}>
      <RNText style={styles.emptyIcon}>{icon}</RNText>
      <Text style={styles.emptyText}>{title}</Text>
      <Text style={styles.emptyHint}>{hint}</Text>
      {children}
    </View>
  );

  // 题库分类筛选：基于保存时归一化后的 subject
  const subjectCounts = SUBJECT_CATEGORIES.reduce((acc, c) => {
    acc[c.value] = c.value === 'all'
      ? myQuestions.length
      : myQuestions.filter((it) => normalizeSubjectForFilter(it.question?.subject) === c.value).length;
    return acc;
  }, {});

  const filteredQuestions = subjectFilter === 'all'
    ? myQuestions
    : myQuestions.filter((it) => normalizeSubjectForFilter(it.question?.subject) === subjectFilter);

  const renderSubjectFilterBar = () => (
    <View style={styles.subjectBar}>
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.subjectBarContent}
      >
        {SUBJECT_CATEGORIES.map((c) => {
          const active = subjectFilter === c.value;
          return (
            <TouchableOpacity
              key={c.value}
              activeOpacity={0.75}
              onPress={() => setSubjectFilter(c.value)}
              style={[styles.subjectChip, active && styles.subjectChipActive]}
            >
              <RNText style={styles.subjectChipIcon}>{c.icon}</RNText>
              <Text style={[styles.subjectChipText, active && styles.subjectChipTextActive]}>
                {c.label}
              </Text>
              <View style={[styles.subjectChipCount, active && styles.subjectChipCountActive]}>
                <Text style={[styles.subjectChipCountText, active && styles.subjectChipCountTextActive]}>
                  {subjectCounts[c.value] || 0}
                </Text>
              </View>
            </TouchableOpacity>
          );
        })}
      </ScrollView>
    </View>
  );

  // ── Main render ──────────────────────────────────────
  return (
    <View style={styles.container}>
      <View style={styles.tabsWrap}>
        <SegmentedButtons
          value={activeTab}
          onValueChange={setActiveTab}
          buttons={[
            { value: 'bank',  label: `题库 ${myQuestions.length}`,      icon: 'notebook' },
            { value: 'kp',    label: `考点 ${knowledgePoints.length}`,  icon: 'tag-multiple' },
            { value: 'rec',   label: '推荐',                             icon: 'star-shooting' },
          ]}
          style={styles.tabs}
          density="regular"
        />
      </View>

      {activeTab === 'bank' && (
        loadingBank ? (
          <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
        ) : myQuestions.length === 0 ? (
          <EmptyState
            icon="📭"
            title="还没有题目记录"
            hint="在 AI 智能问答中拍照上传题目，系统会自动提取并保存到这里"
          />
        ) : (
          <>
            {renderSubjectFilterBar()}
            {filteredQuestions.length === 0 ? (
              <EmptyState
                icon="🗂️"
                title={`「${SUBJECT_CATEGORIES.find(c => c.value === subjectFilter)?.label}」分类暂无题目`}
                hint="切换其他分类，或上传新题目到这一类"
              />
            ) : (
              <FlatList
                data={filteredQuestions}
                keyExtractor={(item) => String(item.id)}
                renderItem={renderQuestionCard}
                contentContainerStyle={styles.list}
              />
            )}
          </>
        )
      )}

      {activeTab === 'kp' && (
        loadingKp ? (
          <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
        ) : knowledgePoints.length === 0 ? (
          <EmptyState
            icon="🏷️"
            title="还没有知识点记录"
            hint="上传题目后，系统会自动提取涉及的知识点"
          />
        ) : (
          <FlatList
            data={knowledgePoints}
            keyExtractor={(item) => String(item.id)}
            renderItem={renderKpCard}
            contentContainerStyle={styles.list}
          />
        )
      )}

      {activeTab === 'rec' && (
        loadingRec ? (
          <ActivityIndicator style={styles.loading} size="large" color={colors.primary} />
        ) : recommendations.length === 0 ? (
          <EmptyState
            icon="🎯"
            title="暂无推荐题目"
            hint="多做几题，系统会根据你的薄弱点和知识点掌握情况精准推荐"
          >
            <Button
              mode="contained"
              onPress={loadRecommendations}
              style={{ marginTop: spacing.lg, borderRadius: radii.md }}
              icon="refresh"
            >
              刷新推荐
            </Button>
          </EmptyState>
        ) : (
          <FlatList
            data={recommendations}
            keyExtractor={(item) => String(item.question.id)}
            renderItem={renderRecommendationCard}
            contentContainerStyle={styles.list}
            ListHeaderComponent={
              <View style={styles.recHeader}>
                <View style={{ flex: 1 }}>
                  <Text style={styles.recHeaderTitle}>🎯 为你精选</Text>
                  <Text style={styles.recHeaderText}>
                    基于薄弱点 + 知识点关联，共 {recommendations.length} 道题
                  </Text>
                </View>
                <Button
                  mode="text"
                  compact
                  onPress={loadRecommendations}
                  icon="refresh"
                  textColor={colors.primary}
                >
                  刷新
                </Button>
              </View>
            }
          />
        )
      )}

      {practiceItem && renderPracticeModal()}

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },

  tabsWrap: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.md,
    paddingBottom: spacing.sm,
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  tabs: {},

  loading: { marginTop: 80 },
  list: {
    padding: spacing.lg,
    paddingBottom: spacing['2xl'],
  },

  // 题库分类筛选条
  subjectBar: {
    backgroundColor: colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  subjectBarContent: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm + 2,
    gap: 8,
  },
  subjectChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 6,
    paddingHorizontal: 12,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    marginRight: 8,
  },
  subjectChipActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
    ...shadows.colored,
  },
  subjectChipIcon: {
    fontSize: 14,
    marginRight: 5,
  },
  subjectChipText: {
    ...typography.bodySm,
    color: colors.textSecondary,
    fontWeight: '700',
  },
  subjectChipTextActive: {
    color: '#FFFFFF',
  },
  subjectChipCount: {
    marginLeft: 6,
    minWidth: 22,
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: radii.pill,
    backgroundColor: colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  subjectChipCountActive: {
    backgroundColor: 'rgba(255,255,255,0.25)',
  },
  subjectChipCountText: {
    fontSize: 10,
    fontWeight: '800',
    color: colors.textTertiary,
  },
  subjectChipCountTextActive: {
    color: '#FFFFFF',
  },

  // Cards
  card: { marginBottom: spacing.md },
  pillRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    alignItems: 'center',
    marginBottom: spacing.sm,
  },
  pill: {
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: radii.pill,
    alignSelf: 'flex-start',
  },
  pillText: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 0.3,
  },
  content: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    lineHeight: 20,
  },
  cardFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: spacing.sm + 2,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  meta: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  footerCta: {
    ...typography.caption,
    color: colors.primary,
    fontWeight: '700',
  },
  reasonText: {
    ...typography.bodySm,
    fontWeight: '600',
    marginBottom: spacing.sm - 2,
  },
  categoryBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 3,
    borderRadius: radii.pill,
  },
  categoryBadgeText: {
    fontSize: 11,
    fontWeight: '700',
  },

  // KP cards
  kpCard: {
    marginBottom: spacing.sm,
  },
  kpRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: spacing.md,
  },
  kpIconWrap: {
    width: 40,
    height: 40,
    borderRadius: radii.md,
    backgroundColor: colors.primarySoft,
    alignItems: 'center',
    justifyContent: 'center',
  },
  kpName: {
    ...typography.titleSm,
    color: colors.textPrimary,
  },
  kpSubject: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },

  // Empty state
  empty: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: spacing['3xl'],
  },
  emptyIcon: {
    fontSize: 56,
    marginBottom: spacing.md,
  },
  emptyText: {
    ...typography.titleMd,
    color: colors.textSecondary,
    marginBottom: spacing.xs,
  },
  emptyHint: {
    ...typography.bodySm,
    color: colors.textTertiary,
    textAlign: 'center',
    lineHeight: 20,
  },

  // Recommendation header
  recHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
    paddingBottom: spacing.md,
  },
  recHeaderTitle: {
    ...typography.titleMd,
    color: colors.textPrimary,
  },
  recHeaderText: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },

  // Modal
  modalOverlay: {
    position: 'absolute',
    top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: colors.background,
    zIndex: 10,
  },
  modal: { flex: 1 },
  modalContent: { padding: spacing.lg, paddingBottom: spacing['3xl'] },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    marginBottom: spacing.md,
  },
  closeIcon: {
    width: 32,
    height: 32,
    borderRadius: 16,
    backgroundColor: colors.surfaceAlt,
    alignItems: 'center',
    justifyContent: 'center',
  },
  questionLabel: {
    ...typography.caption,
    color: colors.primary,
    fontWeight: '700',
    marginBottom: spacing.sm,
  },
  questionText: {
    ...typography.bodyLg,
    color: colors.textPrimary,
    lineHeight: 26,
  },

  // Options
  optionsContainer: { gap: spacing.sm, marginBottom: spacing.md },
  optionItem: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    padding: 12,
    backgroundColor: colors.surface,
  },
  optionSelected: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  optionCorrect: {
    borderColor: colors.success,
    backgroundColor: colors.successSoft,
  },
  optionWrong: {
    borderColor: colors.danger,
    backgroundColor: colors.dangerSoft,
  },
  optionLabelBox: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: colors.textTertiary,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 12,
  },
  optionLabel: {
    color: '#FFFFFF',
    fontWeight: '800',
    fontSize: 13,
  },
  optionContent: {
    flex: 1,
    ...typography.bodyMd,
    color: colors.textPrimary,
  },
  optionMark: {
    color: colors.success,
    marginLeft: 6,
    fontWeight: '800',
    fontSize: 16,
  },

  // Answer / result
  showAnswerBtn: {
    marginTop: spacing.sm,
    borderRadius: radii.md,
  },
  answerLabel: {
    ...typography.titleSm,
    color: colors.success,
    marginBottom: 4,
  },
  answerText: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    lineHeight: 22,
  },
  analysisText: {
    ...typography.bodySm,
    color: colors.textSecondary,
    lineHeight: 20,
    marginTop: 4,
  },

  submitBtn: {
    marginTop: spacing.lg,
    borderRadius: radii.md,
    ...shadows.colored,
  },

  resultTitle: {
    ...typography.titleMd,
    marginBottom: 6,
  },
  resultAnswer: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    marginBottom: 4,
  },

  // Similar
  similarSection: { marginTop: spacing.xl },
  similarTitle: {
    ...typography.titleSm,
    color: colors.textSecondary,
    marginBottom: spacing.sm,
  },
  similarCard: {
    marginBottom: spacing.sm,
    backgroundColor: colors.surfaceAlt,
  },
  similarContent: {
    ...typography.bodySm,
    color: colors.textPrimary,
    marginTop: 4,
  },
  noSimilar: {
    ...typography.bodySm,
    color: colors.textTertiary,
  },

  closeBtn: {
    marginTop: spacing.xl,
    borderRadius: radii.md,
    borderColor: colors.border,
  },
});
