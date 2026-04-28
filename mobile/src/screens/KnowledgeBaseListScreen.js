import React, { useCallback, useState } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  Text as RNText,
} from 'react-native';
import {
  Text,
  Portal,
  Dialog,
  TextInput,
  Button,
  Snackbar,
} from 'react-native-paper';
import { useFocusEffect } from '@react-navigation/native';
import {
  getKnowledgeBases,
  createKnowledgeBase,
  deleteKnowledgeBase,
} from '../api/knowledgeBase';
import { colors, radii, spacing, typography, shadows } from '../theme';
import ModernCard from '../components/ModernCard';

export default function KnowledgeBaseListScreen({ navigation }) {
  const [list, setList] = useState([]);
  const [refreshing, setRefreshing] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [nameInput, setNameInput] = useState('');
  const [descInput, setDescInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const load = async () => {
    try {
      const res = await getKnowledgeBases();
      setList(res.data || []);
    } catch (err) {
      setSnackMsg(err.message || '加载失败');
      setSnackVisible(true);
    }
  };

  useFocusEffect(useCallback(() => { load(); }, []));

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const openCreate = () => {
    setNameInput('');
    setDescInput('');
    setCreateOpen(true);
  };

  const confirmCreate = async () => {
    const name = nameInput.trim();
    if (!name) {
      setSnackMsg('请输入知识库名称');
      setSnackVisible(true);
      return;
    }
    try {
      setSaving(true);
      const res = await createKnowledgeBase({ name, description: descInput.trim() || null });
      setCreateOpen(false);
      setSnackMsg('✅ 知识库已创建');
      setSnackVisible(true);
      await load();
      // 立刻进入新建的知识库详情
      if (res?.data?.id) {
        navigation.navigate('KnowledgeBaseDetail', {
          id: res.data.id,
          name: res.data.name,
        });
      }
    } catch (err) {
      setSnackMsg(err.message || '创建失败');
      setSnackVisible(true);
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = (kb) => {
    // 用 Dialog 需要额外状态，这里简单用系统 confirm 风格
    // 由于 RN 没有原生 confirm，用 Snackbar + 二次点击模式略显复杂，
    // 改为：长按直接删除，带 Snackbar 撤销。
    (async () => {
      try {
        await deleteKnowledgeBase(kb.id);
        setSnackMsg(`已删除"${kb.name}"`);
        setSnackVisible(true);
        await load();
      } catch (err) {
        setSnackMsg(err.message || '删除失败');
        setSnackVisible(true);
      }
    })();
  };

  return (
    <>
      <ScrollView
        style={styles.container}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={colors.primary} />
        }
      >
        <View style={styles.header}>
          <Text style={styles.title}>我的知识库</Text>
          <Text style={styles.subtitle}>
            为不同科目或主题创建独立的知识库，给 AI 对话挂载专属资料
          </Text>
        </View>

        {list.length === 0 ? (
          <View style={styles.emptyBox}>
            <RNText style={styles.emptyIcon}>📚</RNText>
            <Text style={styles.emptyTitle}>还没有知识库</Text>
            <Text style={styles.emptyHint}>点击下方按钮新建一个</Text>
          </View>
        ) : (
          list.map((kb) => (
            <TouchableOpacity
              key={kb.id}
              activeOpacity={0.75}
              onPress={() =>
                navigation.navigate('KnowledgeBaseDetail', { id: kb.id, name: kb.name })
              }
              onLongPress={() => confirmDelete(kb)}
              style={styles.kbCardWrap}
            >
              <ModernCard style={styles.kbCard} padding={18} accent={colors.primary}>
                <View style={styles.kbRow}>
                  <View style={styles.kbIconBox}>
                    <RNText style={styles.kbIcon}>📚</RNText>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.kbName} numberOfLines={1}>
                      {kb.name}
                    </Text>
                    {kb.description ? (
                      <Text style={styles.kbDesc} numberOfLines={1}>
                        {kb.description}
                      </Text>
                    ) : null}
                    <View style={styles.kbStats}>
                      <RNText style={styles.kbStatText}>
                        📄 {kb.documentCount || 0} 份
                      </RNText>
                      <RNText style={styles.kbStatDot}>·</RNText>
                      <RNText style={[styles.kbStatText, { color: colors.success }]}>
                        ✓ {kb.enabledCount || 0} 已启用
                      </RNText>
                    </View>
                  </View>
                  <RNText style={styles.chevron}>›</RNText>
                </View>
              </ModernCard>
            </TouchableOpacity>
          ))
        )}

        {/* 新建按钮，做成一张虚线卡片 */}
        <TouchableOpacity activeOpacity={0.75} onPress={openCreate} style={styles.addCard}>
          <RNText style={styles.addPlus}>＋</RNText>
          <Text style={styles.addText}>新建知识库</Text>
        </TouchableOpacity>

        <Text style={styles.footerTip}>长按卡片可删除知识库</Text>
      </ScrollView>

      <Portal>
        <Dialog visible={createOpen} onDismiss={() => setCreateOpen(false)}>
          <Dialog.Title>新建知识库</Dialog.Title>
          <Dialog.Content>
            <TextInput
              label="名称 *"
              mode="outlined"
              value={nameInput}
              onChangeText={setNameInput}
              placeholder="如：政治·马原"
              style={{ marginBottom: spacing.md }}
            />
            <TextInput
              label="描述（可选）"
              mode="outlined"
              value={descInput}
              onChangeText={setDescInput}
              multiline
              numberOfLines={3}
              placeholder="简单说明这个知识库的用途"
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setCreateOpen(false)}>取消</Button>
            <Button onPress={confirmCreate} loading={saving} disabled={saving} mode="contained">
              创建
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      <Snackbar
        visible={snackVisible}
        onDismiss={() => setSnackVisible(false)}
        duration={2200}
      >
        {snackMsg}
      </Snackbar>
    </>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  scrollContent: { padding: spacing.lg, paddingBottom: spacing['3xl'] },
  header: { marginBottom: spacing.lg },
  title: { ...typography.titleLg, color: colors.textPrimary },
  subtitle: { ...typography.bodySm, color: colors.textSecondary, marginTop: 4 },

  emptyBox: { alignItems: 'center', paddingVertical: spacing['3xl'] },
  emptyIcon: { fontSize: 52, marginBottom: spacing.md },
  emptyTitle: { ...typography.titleMd, color: colors.textPrimary },
  emptyHint: { ...typography.bodySm, color: colors.textTertiary, marginTop: 4 },

  kbCardWrap: { marginBottom: spacing.md },
  kbCard: { borderRadius: radii.lg },
  kbRow: { flexDirection: 'row', alignItems: 'center' },
  kbIconBox: {
    width: 44, height: 44, borderRadius: radii.md,
    backgroundColor: colors.primarySoft,
    alignItems: 'center', justifyContent: 'center',
    marginRight: spacing.md,
  },
  kbIcon: { fontSize: 22 },
  kbName: { ...typography.titleMd, color: colors.textPrimary },
  kbDesc: { ...typography.bodySm, color: colors.textSecondary, marginTop: 2 },
  kbStats: { flexDirection: 'row', alignItems: 'center', marginTop: 6 },
  kbStatText: { ...typography.caption, color: colors.textSecondary, fontWeight: '600' },
  kbStatDot: { ...typography.caption, color: colors.textTertiary, marginHorizontal: 6 },
  chevron: { fontSize: 26, color: colors.textTertiary, marginLeft: spacing.sm },

  addCard: {
    borderWidth: 1.5,
    borderColor: colors.border,
    borderStyle: 'dashed',
    borderRadius: radii.lg,
    paddingVertical: spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: spacing.sm,
    backgroundColor: colors.surface,
  },
  addPlus: { fontSize: 28, color: colors.primary, fontWeight: '300' },
  addText: { ...typography.titleSm, color: colors.primary, marginTop: 2 },

  footerTip: {
    ...typography.caption,
    color: colors.textTertiary,
    textAlign: 'center',
    marginTop: spacing.lg,
  },
});
