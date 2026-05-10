import React, { useEffect, useState } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  Text as RNText,
  Image,
  ActivityIndicator,
} from 'react-native';
import {
  Text,
  Button,
  Snackbar,
  ProgressBar,
  Portal,
  Dialog,
  TextInput,
  RadioButton,
} from 'react-native-paper';
import * as ImagePicker from 'expo-image-picker';
import * as FileSystem from 'expo-file-system';
import { getUserInfo, checkIn, updateProfile, uploadAvatar } from '../api/auth';
import { getSubjectAnalysis } from '../api/analysis';
import dayjs from 'dayjs';
import { colors, radii, spacing, typography } from '../theme';
import ModernCard from '../components/ModernCard';
import SectionLabel from '../components/SectionLabel';

const SUBJECT_COLORS = {
  '政治': '#EF4444',
  '英语': '#3B82F6',
  '数学': '#8B5CF6',
  '专业课': '#10B981',
};

const getSubjectColor = (s) => SUBJECT_COLORS[s] || colors.primary;

// 目标专业可选项（未来扩展这里即可）
const MAJOR_OPTIONS = [
  { value: '计算机科学与技术（408）', label: '计算机科学与技术', code: '408' },
];

// 考试年份可选项（自动生成：今年到今年+4）
const CURRENT_YEAR = new Date().getFullYear();
const EXAM_YEARS = Array.from({ length: 5 }, (_, i) => CURRENT_YEAR + i);
const examDateOfYear = (year) => `${year}-12-21`;

export default function DashboardScreen({ navigation }) {
  const [userInfo, setUserInfo] = useState({});
  const [subjectStats, setSubjectStats] = useState({});
  const [refreshing, setRefreshing] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  // 弹窗状态
  const [schoolDialogOpen, setSchoolDialogOpen] = useState(false);
  const [schoolInput, setSchoolInput] = useState('');
  const [majorDialogOpen, setMajorDialogOpen] = useState(false);
  const [majorChoice, setMajorChoice] = useState('');
  const [studyDateDialogOpen, setStudyDateDialogOpen] = useState(false);
  const [studyYearInput, setStudyYearInput] = useState('');
  const [studyMonthInput, setStudyMonthInput] = useState('');
  const [studyDayInput, setStudyDayInput] = useState('');
  const [examDialogOpen, setExamDialogOpen] = useState(false);
  const [examYearChoice, setExamYearChoice] = useState(null);
  const [saving, setSaving] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);

  const loadData = async () => {
    try {
      const res = await getUserInfo();
      setUserInfo(res.data);
    } catch (err) {
      console.error(err);
    }
    try {
      const res = await getSubjectAnalysis();
      setSubjectStats(res.data || {});
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadData(); }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  // ── 头像上传 ────────────────────────────────────────
  // Web / 部分 Android 设备 allowsEditing 裁剪后 asset.base64 会是 undefined，
  // 此时通过 fetch(uri) → blob → FileReader 兜底把图片转成 base64
  const uriToBase64 = async (uri) => {
    console.log('[avatar] uriToBase64 start:', uri);
    // 优先使用 expo-file-system（更可靠，支持 content:// 等 URI）
    try {
      const b64 = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64,
      });
      console.log('[avatar] FileSystem base64 length:', b64?.length);
      if (b64 && b64.length > 10) return b64;
    } catch (fsErr) {
      console.warn('[avatar] FileSystem 读取失败，尝试 fetch 兜底:', fsErr.message);
    }
    // fallback: fetch + FileReader
    const res = await fetch(uri);
    console.log('[avatar] fetch status:', res.status, 'ok:', res.ok);
    const blob = await res.blob();
    console.log('[avatar] blob size:', blob.size, 'type:', blob.type);
    if (!blob || blob.size === 0) {
      throw new Error('图片数据为空');
    }
    return await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const r = reader.result || '';
        if (!r || (typeof r === 'string' && r.length < 10)) {
          reject(new Error('图片编码结果异常'));
          return;
        }
        const idx = typeof r === 'string' ? r.indexOf(',') : -1;
        const b64 = idx >= 0 ? r.substring(idx + 1) : r;
        console.log('[avatar] fetch base64 length:', b64.length);
        resolve(b64);
      };
      reader.onerror = () => reject(new Error('图片读取失败'));
      reader.readAsDataURL(blob);
    });
  };

  const handlePickAvatar = async () => {
    if (uploadingAvatar) return;
    console.log('[avatar] ====== 开始选择头像 ======');
    try {
      const perm = await ImagePicker.requestMediaLibraryPermissionsAsync();
      console.log('[avatar] 权限结果:', perm.granted);
      if (!perm.granted) {
        setSnackMsg('需要相册权限才能更换头像');
        setSnackVisible(true);
        return;
      }
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ImagePicker.MediaTypeOptions.Images,
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.6,
        base64: true,
      });
      console.log('[avatar] picker result canceled:', result.canceled);
      if (result.canceled) return;

      const asset = result.assets?.[0];
      console.log('[avatar] asset.uri:', asset?.uri, 'base64 available:', !!asset?.base64, 'mimeType:', asset?.mimeType, 'width:', asset?.width, 'height:', asset?.height);
      if (!asset || (!asset.base64 && !asset.uri)) {
        setSnackMsg('图片读取失败，请重试');
        setSnackVisible(true);
        return;
      }

      setUploadingAvatar(true);
      let base64 = asset.base64;
      if (!base64) {
        console.log('[avatar] base64 为空，走 uriToBase64 兜底');
        try {
          base64 = await uriToBase64(asset.uri);
        } catch (e) {
          console.error('[avatar] uriToBase64 失败:', e);
          setSnackMsg('图片解析失败：' + (e.message || '未知错误'));
          setSnackVisible(true);
          return;
        }
      }
      if (!base64) {
        setSnackMsg('图片数据为空，请换一张图试试');
        setSnackVisible(true);
        return;
      }

      const mime = asset.mimeType || 'image/jpeg';
      const dataUri = `data:${mime};base64,${base64}`;
      console.log('[avatar] 开始上传, dataUri 长度:', dataUri.length);
      const res = await uploadAvatar(dataUri);
      console.log('[avatar] 上传响应:', JSON.stringify(res));
      const avatarUrl = res?.data?.avatar || dataUri;
      console.log('[avatar] 最终 avatar URL 长度:', avatarUrl.length);
      setUserInfo((prev) => ({
        ...prev,
        avatar: avatarUrl,
      }));
      setSnackMsg('✅ 头像已更新');
      setSnackVisible(true);
    } catch (err) {
      console.error('[avatar] 异常:', err);
      setSnackMsg(err.message || '头像上传失败');
      setSnackVisible(true);
    } finally {
      setUploadingAvatar(false);
    }
  };

  const [checkingIn, setCheckingIn] = useState(false);

  const handleCheckIn = async () => {
    if (userInfo.checkedInToday || checkingIn) return;
    setCheckingIn(true);
    try {
      await checkIn(0);
      // 立刻更新本地状态：按钮变绿色"已打卡"
      setUserInfo((prev) => ({
        ...prev,
        checkedInToday: true,
        checkInDays: (prev.checkInDays || 0) + 1,
      }));
      setSnackMsg('🎉 打卡成功！连续坚持，未来可期');
      setSnackVisible(true);
      // 后台同步真实数据
      loadData();
    } catch (err) {
      setSnackMsg(err.message || '打卡失败');
      setSnackVisible(true);
    } finally {
      setCheckingIn(false);
    }
  };

  // 退出登录按钮已由 AppNavigator 的 ProfileStack headerRight 统一提供

  // ── 保存 profile（只传变更字段，其他保持） ────────────
  const saveProfile = async (patch) => {
    setSaving(true);
    try {
      await updateProfile({
        targetSchool: patch.targetSchool ?? userInfo.targetSchool ?? null,
        targetMajor: patch.targetMajor ?? userInfo.targetMajor ?? null,
        examDate: patch.examDate ?? userInfo.examDate ?? null,
        studyStartDate: patch.studyStartDate ?? userInfo.studyStartDate ?? null,
      });
      setSnackMsg('✅ 保存成功');
      setSnackVisible(true);
      await loadData();
    } catch (err) {
      setSnackMsg(err.message || '保存失败');
      setSnackVisible(true);
    } finally {
      setSaving(false);
    }
  };

  // ── 目标院校 ────────────────────────────────────────
  const openSchoolDialog = () => {
    setSchoolInput(userInfo.targetSchool || '');
    setSchoolDialogOpen(true);
  };
  const confirmSchool = async () => {
    const val = schoolInput.trim();
    if (!val) { setSnackMsg('请输入院校名称'); setSnackVisible(true); return; }
    setSchoolDialogOpen(false);
    await saveProfile({ targetSchool: val });
  };

  // ── 目标专业 ────────────────────────────────────────
  const openMajorDialog = () => {
    setMajorChoice(userInfo.targetMajor || MAJOR_OPTIONS[0].value);
    setMajorDialogOpen(true);
  };
  const confirmMajor = async () => {
    setMajorDialogOpen(false);
    await saveProfile({ targetMajor: majorChoice });
  };

  // ── 开始备考日期 ─────────────────────────────────────
  const openStudyDateDialog = () => {
    const d = userInfo.studyStartDate ? dayjs(userInfo.studyStartDate) : dayjs();
    setStudyYearInput(String(d.year()));
    setStudyMonthInput(String(d.month() + 1));
    setStudyDayInput(String(d.date()));
    setStudyDateDialogOpen(true);
  };
  const confirmStudyDate = async () => {
    const y = parseInt(studyYearInput, 10);
    const m = parseInt(studyMonthInput, 10);
    const d = parseInt(studyDayInput, 10);
    if (!y || !m || !d || m < 1 || m > 12 || d < 1 || d > 31) {
      setSnackMsg('请输入合法日期'); setSnackVisible(true); return;
    }
    const dateStr = `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
    if (!dayjs(dateStr, 'YYYY-MM-DD', true).isValid()) {
      setSnackMsg('日期无效'); setSnackVisible(true); return;
    }
    setStudyDateDialogOpen(false);
    await saveProfile({ studyStartDate: dateStr });
  };
  const pickTodayForStudy = async () => {
    setStudyDateDialogOpen(false);
    await saveProfile({ studyStartDate: dayjs().format('YYYY-MM-DD') });
  };

  // ── 考试年份（自动填 12-21） ─────────────────────────
  const openExamDialog = () => {
    const currentYear = userInfo.examDate ? dayjs(userInfo.examDate).year() : CURRENT_YEAR + 1;
    setExamYearChoice(currentYear);
    setExamDialogOpen(true);
  };
  const confirmExam = async () => {
    if (!examYearChoice) { setSnackMsg('请选择年份'); setSnackVisible(true); return; }
    setExamDialogOpen(false);
    await saveProfile({ examDate: examDateOfYear(examYearChoice) });
  };

  const daysUntilExam = userInfo.examDate
    ? dayjs(userInfo.examDate).diff(dayjs(), 'day')
    : null;

  const subjectEntries = Object.entries(subjectStats);
  const username = userInfo.username || userInfo.nickname || '同学';
  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 6) return '夜深了';
    if (h < 12) return '早上好';
    if (h < 18) return '下午好';
    return '晚上好';
  })();

  // ── Editable row 小组件 ──────────────────────────────
  const EditableRow = ({ icon, label, value, placeholder, onPress }) => (
    <TouchableOpacity style={styles.editRow} onPress={onPress} activeOpacity={0.6}>
      <RNText style={styles.editIcon}>{icon}</RNText>
      <View style={{ flex: 1 }}>
        <Text style={styles.editLabel}>{label}</Text>
        <Text style={[
          styles.editValue,
          !value && { color: colors.textTertiary, fontStyle: 'italic' },
        ]}>
          {value || placeholder}
        </Text>
      </View>
      <RNText style={styles.editArrow}>›</RNText>
    </TouchableOpacity>
  );

  return (
    <>
      <ScrollView
        style={styles.container}
        contentContainerStyle={styles.scrollContent}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />}
      >
        {/* 顶部 Hero */}
        <View style={styles.hero}>
          <View style={[styles.heroLayer, { backgroundColor: colors.gradientStart }]} />
          <View style={[styles.heroLayer, { backgroundColor: colors.gradientEnd, opacity: 0.5 }]} />
          <View style={styles.heroDot1} />
          <View style={styles.heroDot2} />

          <View style={styles.heroContent}>
            <View style={styles.heroTopRow}>
              <TouchableOpacity
                activeOpacity={0.85}
                onPress={handlePickAvatar}
                style={styles.avatarWrap}
              >
                {userInfo.avatar ? (
                  <Image
                    source={{ uri: userInfo.avatar }}
                    style={styles.avatarImg}
                    resizeMode="cover"
                    onError={(e) => console.error('[avatar] Image 加载失败:', e.nativeEvent?.error, 'uri 前100字符:', userInfo.avatar?.substring(0, 100))}
                    onLoad={() => console.log('[avatar] Image 加载成功')}
                  />
                ) : (
                  <View style={styles.avatarPlaceholder}>
                    <RNText style={styles.avatarPlaceholderText}>👤</RNText>
                  </View>
                )}
                <View style={styles.avatarEditBadge}>
                  {uploadingAvatar ? (
                    <ActivityIndicator size={10} color="#FFFFFF" />
                  ) : (
                    <RNText style={styles.avatarEditIcon}>✎</RNText>
                  )}
                </View>
              </TouchableOpacity>
              <View style={{ flex: 1, marginLeft: spacing.md }}>
                <Text style={styles.heroGreeting}>{greeting}，{username} 👋</Text>
                <Text style={styles.heroSlogan}>今天也要向目标再靠近一点</Text>
              </View>
            </View>

            {daysUntilExam != null && (
              <View style={styles.countdownBox}>
                <Text style={styles.countdownLabel}>距离考试还有</Text>
                <View style={styles.countdownRow}>
                  <Text style={[styles.countdownNumber, daysUntilExam < 30 && styles.countdownDanger]}>
                    {daysUntilExam}
                  </Text>
                  <Text style={styles.countdownUnit}>天</Text>
                </View>
                {userInfo.examDate ? (
                  <Text style={styles.countdownDate}>📅 {userInfo.examDate}</Text>
                ) : null}
              </View>
            )}
          </View>
        </View>

        {/* 统计卡片网格 */}
        <View style={styles.statsGrid}>
          <ModernCard style={styles.statCard} padding={16} elevation="sm">
            <View style={[styles.statIconWrap, { backgroundColor: colors.successSoft }]}>
              <RNText style={styles.statIcon}>🔥</RNText>
            </View>
            <Text style={styles.statValue}>{userInfo.checkInDays || 0}</Text>
            <Text style={styles.statLabel}>累计打卡 · 天</Text>
          </ModernCard>

          <ModernCard style={styles.statCard} padding={16} elevation="sm">
            <View style={[styles.statIconWrap, { backgroundColor: colors.primarySoft }]}>
              <RNText style={styles.statIcon}>⏱️</RNText>
            </View>
            <Text style={styles.statValue}>{userInfo.totalStudyMinutes || 0}</Text>
            <Text style={styles.statLabel}>学习 · 分钟</Text>
          </ModernCard>
        </View>

        {/* 今日打卡 */}
        <ModernCard
          style={styles.card}
          padding={18}
          accent={userInfo.checkedInToday ? colors.success : colors.secondary}
        >
          <View style={styles.cardHeader}>
            <View style={{ flex: 1 }}>
              <Text style={styles.cardTitle}>
                {userInfo.checkedInToday ? '✅ 今日已打卡' : '今日打卡'}
              </Text>
              <Text style={styles.cardSubtitle}>
                {userInfo.checkedInToday
                  ? `已连续打卡 ${userInfo.checkInDays || 0} 天，明天继续加油！`
                  : '坚持打卡，养成稳定的学习节奏'}
              </Text>
            </View>
            <Button
              mode="contained"
              onPress={handleCheckIn}
              style={[
                styles.checkBtn,
                userInfo.checkedInToday && styles.checkBtnDone,
              ]}
              buttonColor={userInfo.checkedInToday ? colors.success : colors.secondary}
              textColor="#FFFFFF"
              labelStyle={{ fontWeight: '700' }}
              icon={userInfo.checkedInToday ? 'check-bold' : 'check-decagram'}
              loading={checkingIn}
            >
              {userInfo.checkedInToday ? '已打卡' : '打卡'}
            </Button>
          </View>
        </ModernCard>

        {/* 备考档案（可编辑） */}
        <ModernCard style={styles.card} padding={18}>
          <SectionLabel icon="🎯" color={colors.primary}>备考档案</SectionLabel>
          <Text style={styles.hint}>点击每一项进行修改，数据自动保存到你的账户</Text>

          <EditableRow
            icon="🏫"
            label="目标院校"
            value={userInfo.targetSchool}
            placeholder="点击设置目标院校"
            onPress={openSchoolDialog}
          />
          <View style={styles.divider} />
          <EditableRow
            icon="📚"
            label="目标专业"
            value={userInfo.targetMajor}
            placeholder="点击选择目标专业"
            onPress={openMajorDialog}
          />
          <View style={styles.divider} />
          <EditableRow
            icon="🚀"
            label="开始备考"
            value={userInfo.studyStartDate}
            placeholder="点击选择开始日期"
            onPress={openStudyDateDialog}
          />
          <View style={styles.divider} />
          <EditableRow
            icon="📅"
            label="考试日期"
            value={userInfo.examDate ? `${userInfo.examDate}（${dayjs(userInfo.examDate).year()} 考研）` : null}
            placeholder="点击选择考试年份"
            onPress={openExamDialog}
          />
        </ModernCard>

        {/* 各科掌握度 */}
        <ModernCard style={styles.card} padding={18}>
          <SectionLabel icon="📊" color={colors.primary}>各科掌握情况</SectionLabel>

          {subjectEntries.length === 0 ? (
            <View style={styles.emptyBox}>
              <RNText style={styles.emptyIcon}>📭</RNText>
              <Text style={styles.emptyText}>暂无做题数据</Text>
              <Text style={styles.emptyHint}>开始做题后这里将显示各科掌握度</Text>
            </View>
          ) : (
            subjectEntries.map(([subject, stats]) => {
              const color = getSubjectColor(subject);
              const pct = (stats.averageMastery || 0) / 100;
              return (
                <View key={subject} style={styles.subjectBlock}>
                  <View style={styles.subjectHead}>
                    <View style={styles.subjectLabel}>
                      <View style={[styles.subjectBadge, { backgroundColor: color }]}>
                        <Text style={styles.subjectBadgeText}>{subject}</Text>
                      </View>
                      <Text style={styles.subjectPct}>{stats.averageMastery}%</Text>
                    </View>
                    <Text style={styles.subjectMeta}>
                      {stats.totalAttempts} 次 · {stats.knowledgePointCount} 考点
                    </Text>
                  </View>
                  <ProgressBar
                    progress={pct}
                    color={color}
                    style={styles.progress}
                  />
                </View>
              );
            })
          )}
        </ModernCard>

        {/* 知识图谱入口卡片 */}
        <TouchableOpacity
          activeOpacity={0.85}
          onPress={() => navigation.getParent()?.navigate('AnalysisTab', {
            screen: 'KnowledgeGraph',
          })}
        >
          <ModernCard style={styles.card} padding={18}>
            <View style={styles.graphCardRow}>
              <View style={styles.graphCardLeft}>
                <RNText style={styles.graphCardEmoji}>🗺️</RNText>
                <View>
                  <Text style={styles.graphCardTitle}>知识图谱</Text>
                  <Text style={styles.graphCardSub}>看看你的学习地图，AI 给出诊断建议</Text>
                </View>
              </View>
              <RNText style={styles.graphCardArrow}>›</RNText>
            </View>
          </ModernCard>
        </TouchableOpacity>

      </ScrollView>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={3000}>
        {snackMsg}
      </Snackbar>

      {/* ── 弹窗们（放在 Portal 里保证层级） ──────────────── */}
      <Portal>
        {/* 目标院校 */}
        <Dialog visible={schoolDialogOpen} onDismiss={() => setSchoolDialogOpen(false)} style={styles.dialog}>
          <Dialog.Title>🏫 目标院校</Dialog.Title>
          <Dialog.Content>
            <TextInput
              mode="outlined"
              label="院校名称"
              value={schoolInput}
              onChangeText={setSchoolInput}
              placeholder="例如：清华大学"
              autoFocus
              outlineColor={colors.border}
              activeOutlineColor={colors.primary}
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setSchoolDialogOpen(false)}>取消</Button>
            <Button mode="contained" onPress={confirmSchool} loading={saving}>保存</Button>
          </Dialog.Actions>
        </Dialog>

        {/* 目标专业 */}
        <Dialog visible={majorDialogOpen} onDismiss={() => setMajorDialogOpen(false)} style={styles.dialog}>
          <Dialog.Title>📚 选择目标专业</Dialog.Title>
          <Dialog.Content>
            <RadioButton.Group onValueChange={setMajorChoice} value={majorChoice}>
              {MAJOR_OPTIONS.map((opt) => (
                <TouchableOpacity
                  key={opt.value}
                  style={[
                    styles.majorItem,
                    majorChoice === opt.value && styles.majorItemActive,
                  ]}
                  onPress={() => setMajorChoice(opt.value)}
                  activeOpacity={0.7}
                >
                  <RadioButton.Android value={opt.value} color={colors.primary} />
                  <View style={{ flex: 1 }}>
                    <Text style={styles.majorLabel}>{opt.label}</Text>
                    <Text style={styles.majorCode}>统考代码 {opt.code}</Text>
                  </View>
                </TouchableOpacity>
              ))}
            </RadioButton.Group>
            <Text style={styles.dialogHint}>更多专业即将开放…</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setMajorDialogOpen(false)}>取消</Button>
            <Button mode="contained" onPress={confirmMajor} loading={saving}>保存</Button>
          </Dialog.Actions>
        </Dialog>

        {/* 开始备考日期 */}
        <Dialog visible={studyDateDialogOpen} onDismiss={() => setStudyDateDialogOpen(false)} style={styles.dialog}>
          <Dialog.Title>🚀 开始备考日期</Dialog.Title>
          <Dialog.Content>
            <View style={styles.dateRow}>
              <TextInput
                mode="outlined"
                label="年"
                value={studyYearInput}
                onChangeText={setStudyYearInput}
                keyboardType="number-pad"
                maxLength={4}
                style={[styles.dateField, { flex: 1.2 }]}
                outlineColor={colors.border}
                activeOutlineColor={colors.primary}
              />
              <TextInput
                mode="outlined"
                label="月"
                value={studyMonthInput}
                onChangeText={setStudyMonthInput}
                keyboardType="number-pad"
                maxLength={2}
                style={styles.dateField}
                outlineColor={colors.border}
                activeOutlineColor={colors.primary}
              />
              <TextInput
                mode="outlined"
                label="日"
                value={studyDayInput}
                onChangeText={setStudyDayInput}
                keyboardType="number-pad"
                maxLength={2}
                style={styles.dateField}
                outlineColor={colors.border}
                activeOutlineColor={colors.primary}
              />
            </View>
            <Button
              mode="text"
              onPress={pickTodayForStudy}
              icon="calendar-today"
              style={{ marginTop: spacing.sm, alignSelf: 'flex-start' }}
            >
              使用今天 ({dayjs().format('YYYY-MM-DD')})
            </Button>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setStudyDateDialogOpen(false)}>取消</Button>
            <Button mode="contained" onPress={confirmStudyDate} loading={saving}>保存</Button>
          </Dialog.Actions>
        </Dialog>

        {/* 考试年份 */}
        <Dialog visible={examDialogOpen} onDismiss={() => setExamDialogOpen(false)} style={styles.dialog}>
          <Dialog.Title>📅 选择考试年份</Dialog.Title>
          <Dialog.Content>
            <Text style={styles.dialogHint}>选择年份后，考试日期将自动填为该年 12 月 21 日（研究生入学考试第一天）</Text>
            <View style={styles.yearGrid}>
              {EXAM_YEARS.map((year) => {
                const selected = examYearChoice === year;
                return (
                  <TouchableOpacity
                    key={year}
                    style={[styles.yearBox, selected && styles.yearBoxActive]}
                    onPress={() => setExamYearChoice(year)}
                    activeOpacity={0.7}
                  >
                    <Text style={[styles.yearText, selected && styles.yearTextActive]}>
                      {year}
                    </Text>
                    <Text style={[styles.yearSub, selected && styles.yearSubActive]}>
                      12-21
                    </Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setExamDialogOpen(false)}>取消</Button>
            <Button mode="contained" onPress={confirmExam} loading={saving}>保存</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colors.background,
  },
  scrollContent: {
    paddingBottom: spacing['2xl'],
  },
  // Hero
  hero: {
    borderBottomLeftRadius: radii.xl,
    borderBottomRightRadius: radii.xl,
    overflow: 'hidden',
    marginBottom: spacing.lg,
    minHeight: 180,
  },
  heroLayer: { ...StyleSheet.absoluteFillObject },
  heroDot1: {
    position: 'absolute', right: -50, top: -50,
    width: 170, height: 170, borderRadius: 85,
    backgroundColor: '#FFFFFF', opacity: 0.08,
  },
  heroDot2: {
    position: 'absolute', left: -30, bottom: -40,
    width: 120, height: 120, borderRadius: 60,
    backgroundColor: '#FFFFFF', opacity: 0.06,
  },
  heroContent: { padding: spacing.xl, paddingTop: spacing['2xl'] },
  heroTopRow: { flexDirection: 'row', alignItems: 'center' },
  heroGreeting: { ...typography.titleLg, color: '#FFFFFF' },
  heroSlogan: { ...typography.bodySm, color: 'rgba(255,255,255,0.85)', marginTop: 4 },
  avatarWrap: {
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    borderColor: 'rgba(255,255,255,0.6)',
    backgroundColor: 'rgba(255,255,255,0.15)',
    position: 'relative',
    overflow: 'visible',
  },
  avatarImg: {
    width: '100%',
    height: '100%',
    borderRadius: 32,
  },
  avatarPlaceholder: {
    width: '100%',
    height: '100%',
    borderRadius: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarPlaceholderText: { fontSize: 28 },
  avatarEditBadge: {
    position: 'absolute',
    right: -2,
    bottom: -2,
    width: 20,
    height: 20,
    borderRadius: 10,
    backgroundColor: colors.primary,
    borderWidth: 2,
    borderColor: '#FFFFFF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarEditIcon: { color: '#FFFFFF', fontSize: 10, fontWeight: '700' },
  countdownBox: {
    marginTop: spacing.lg,
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: radii.md,
    paddingVertical: spacing.md,
    paddingHorizontal: spacing.lg,
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.25)',
  },
  countdownLabel: { ...typography.caption, color: 'rgba(255,255,255,0.85)' },
  countdownRow: { flexDirection: 'row', alignItems: 'flex-end', gap: 4 },
  countdownNumber: { fontSize: 32, fontWeight: '800', color: '#FFFFFF', letterSpacing: -1 },
  countdownDanger: { color: '#FEF3C7' },
  countdownUnit: { ...typography.titleSm, color: '#FFFFFF', marginBottom: 4 },
  countdownDate: { ...typography.caption, color: 'rgba(255,255,255,0.82)', marginTop: 2 },

  // Stats grid
  statsGrid: {
    flexDirection: 'row', gap: spacing.md,
    paddingHorizontal: spacing.lg, marginBottom: spacing.md,
  },
  statCard: { flex: 1 },
  statIconWrap: {
    width: 36, height: 36, borderRadius: radii.sm,
    alignItems: 'center', justifyContent: 'center', marginBottom: spacing.sm,
  },
  statIcon: { fontSize: 18 },
  statValue: { fontSize: 24, fontWeight: '800', color: colors.textPrimary, letterSpacing: -0.5 },
  statLabel: { ...typography.caption, color: colors.textSecondary, marginTop: 2 },

  // Card
  card: { marginHorizontal: spacing.lg, marginBottom: spacing.md },
  cardHeader: { flexDirection: 'row', alignItems: 'center' },
  cardTitle: { ...typography.titleMd, color: colors.textPrimary },
  cardSubtitle: { ...typography.caption, color: colors.textSecondary, marginTop: 2 },

  // 知识图谱入口卡片
  graphCardRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  graphCardLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  graphCardEmoji: { fontSize: 30, marginRight: 12 },
  graphCardTitle: {
    ...typography.titleMd,
    color: colors.textPrimary,
    marginBottom: 2,
  },
  graphCardSub: {
    ...typography.caption,
    color: colors.textSecondary,
  },
  graphCardArrow: {
    fontSize: 26,
    color: colors.textTertiary,
    fontWeight: '300',
    marginLeft: 8,
  },

  checkBtn: { borderRadius: radii.pill },
  checkBtnDone: {
    // 已打卡视觉：保持绿色，微微透明 + 不再凸起
    opacity: 0.92,
  },

  hint: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: 2,
    marginBottom: spacing.md,
  },

  // Editable rows
  editRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
  },
  editIcon: {
    fontSize: 22,
    marginRight: spacing.md,
    width: 28,
    textAlign: 'center',
  },
  editLabel: {
    ...typography.caption,
    color: colors.textSecondary,
    marginBottom: 2,
  },
  editValue: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    fontWeight: '600',
  },
  editArrow: {
    fontSize: 24,
    color: colors.textTertiary,
    marginLeft: spacing.sm,
  },
  divider: {
    height: 1,
    backgroundColor: colors.borderLight,
    marginLeft: 28 + spacing.md,
  },

  // Subjects
  subjectBlock: { marginBottom: spacing.md },
  subjectHead: {
    flexDirection: 'row', justifyContent: 'space-between',
    alignItems: 'center', marginBottom: 6,
  },
  subjectLabel: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  subjectBadge: { paddingHorizontal: 10, paddingVertical: 3, borderRadius: radii.pill },
  subjectBadgeText: { color: '#FFFFFF', fontSize: 12, fontWeight: '700' },
  subjectPct: { ...typography.titleSm, color: colors.textPrimary },
  subjectMeta: { ...typography.caption, color: colors.textTertiary },
  progress: { height: 6, borderRadius: 3, backgroundColor: colors.surfaceAlt },

  // Empty
  emptyBox: { alignItems: 'center', paddingVertical: spacing['2xl'] },
  emptyIcon: { fontSize: 40, marginBottom: spacing.sm },
  emptyText: { ...typography.titleSm, color: colors.textSecondary },
  emptyHint: { ...typography.caption, color: colors.textTertiary, marginTop: 4 },

  // Dialog
  dialog: {
    borderRadius: radii.lg,
    backgroundColor: colors.surface,
  },
  dialogHint: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: spacing.sm,
    lineHeight: 18,
  },

  // Major picker
  majorItem: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 8,
    borderRadius: radii.md,
    borderWidth: 1,
    borderColor: colors.border,
    marginBottom: 8,
  },
  majorItemActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  majorLabel: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    fontWeight: '600',
  },
  majorCode: {
    ...typography.caption,
    color: colors.textSecondary,
    marginTop: 2,
  },

  // Study date
  dateRow: {
    flexDirection: 'row',
    gap: spacing.sm,
  },
  dateField: {
    flex: 1,
    backgroundColor: colors.surface,
  },

  // Year grid
  yearGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.sm,
    marginTop: spacing.md,
  },
  yearBox: {
    width: '31%',
    paddingVertical: 14,
    alignItems: 'center',
    borderRadius: radii.md,
    borderWidth: 1.5,
    borderColor: colors.border,
    backgroundColor: colors.surface,
  },
  yearBoxActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  yearText: {
    ...typography.titleMd,
    color: colors.textPrimary,
    fontWeight: '700',
  },
  yearTextActive: {
    color: colors.primary,
  },
  yearSub: {
    ...typography.caption,
    color: colors.textTertiary,
    marginTop: 2,
  },
  yearSubActive: {
    color: colors.primaryDark,
  },
});
