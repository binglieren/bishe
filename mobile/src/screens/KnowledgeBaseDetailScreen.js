import React, { useCallback, useState } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  RefreshControl,
  TouchableOpacity,
  Platform,
  Text as RNText,
} from 'react-native';
import {
  Text,
  Button,
  Snackbar,
  Switch,
  ActivityIndicator,
  IconButton,
} from 'react-native-paper';
import * as DocumentPicker from 'expo-document-picker';
import { useFocusEffect } from '@react-navigation/native';
import dayjs from 'dayjs';
import {
  uploadDocument,
  getDocuments,
  deleteDocument,
  setDocumentEnabled,
} from '../api/document';
import { colors, radii, spacing, typography } from '../theme';
import ModernCard from '../components/ModernCard';

const statusMap = {
  PROCESSING: { color: colors.info, text: '处理中' },
  COMPLETED: { color: colors.success, text: '已完成' },
  FAILED: { color: colors.danger, text: '失败' },
};

const formatSize = (bytes) => {
  if (!bytes) return '-';
  const kb = bytes / 1024;
  if (kb < 1024) return `${kb.toFixed(1)} KB`;
  return `${(kb / 1024).toFixed(1)} MB`;
};

export default function KnowledgeBaseDetailScreen({ route, navigation }) {
  const { id: kbId, name: kbName } = route.params;
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const load = async () => {
    try {
      const res = await getDocuments(kbId);
      setDocuments(res.data || []);
    } catch (err) {
      setSnackMsg(err.message || '加载失败');
      setSnackVisible(true);
    }
  };

  useFocusEffect(useCallback(() => { load(); }, [kbId]));

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const handleUpload = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['application/pdf', 'text/plain'],
        copyToCacheDirectory: true,
      });
      if (result.canceled) return;
      setUploading(true);
      const asset = result.assets[0];
      const filePayload =
        Platform.OS === 'web' && asset.file
          ? asset.file
          : {
              uri: asset.uri,
              name: asset.name,
              type: asset.mimeType || 'application/octet-stream',
            };
      await uploadDocument(kbId, filePayload);
      setSnackMsg('上传成功，正在后台解析…');
      setSnackVisible(true);
      await load();
    } catch (err) {
      setSnackMsg(err.message || '上传失败');
      setSnackVisible(true);
    } finally {
      setUploading(false);
    }
  };

  const handleToggle = async (doc) => {
    const next = !doc.enabled;
    // 本地先更新（乐观更新），失败回滚
    setDocuments((prev) =>
      prev.map((d) => (d.id === doc.id ? { ...d, enabled: next } : d)),
    );
    try {
      await setDocumentEnabled(doc.id, next);
    } catch (err) {
      setSnackMsg(err.message || '切换失败');
      setSnackVisible(true);
      setDocuments((prev) =>
        prev.map((d) => (d.id === doc.id ? { ...d, enabled: doc.enabled } : d)),
      );
    }
  };

  const handleDelete = async (doc) => {
    try {
      await deleteDocument(doc.id);
      setSnackMsg(`已删除 ${doc.originalFilename}`);
      setSnackVisible(true);
      await load();
    } catch (err) {
      setSnackMsg(err.message || '删除失败');
      setSnackVisible(true);
    }
  };

  const enabledCount = documents.filter((d) => d.enabled).length;

  return (
    <>
      <ScrollView
        style={styles.container}
        contentContainerStyle={styles.scrollContent}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
          />
        }
      >
        <ModernCard style={styles.summary} padding={18} accent={colors.primary}>
          <Text style={styles.kbTitle} numberOfLines={1}>
            📚 {kbName}
          </Text>
          <Text style={styles.kbMeta}>
            共 {documents.length} 份资料 · {enabledCount} 份已启用
          </Text>
          <Text style={styles.kbHint}>
            只有"已启用"的资料会在 AI 对话挂载此知识库时参与检索
          </Text>
        </ModernCard>

        <Button
          mode="contained"
          icon="upload"
          onPress={handleUpload}
          loading={uploading}
          disabled={uploading}
          style={styles.uploadBtn}
          contentStyle={{ paddingVertical: 4 }}
        >
          {uploading ? '上传中…' : '上传新资料'}
        </Button>

        {documents.length === 0 ? (
          <View style={styles.emptyBox}>
            <RNText style={styles.emptyIcon}>📄</RNText>
            <Text style={styles.emptyTitle}>该知识库暂无资料</Text>
            <Text style={styles.emptyHint}>点击上方按钮添加 PDF 或文本文件</Text>
          </View>
        ) : (
          documents.map((doc) => {
            const s = statusMap[doc.status] || statusMap.PROCESSING;
            return (
              <ModernCard key={doc.id} style={styles.docCard} padding={14}>
                <View style={styles.docHeader}>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.docName} numberOfLines={1}>
                      📄 {doc.originalFilename}
                    </Text>
                    <View style={styles.docMeta}>
                      <RNText style={[styles.statusChip, { color: s.color, borderColor: s.color }]}>
                        {s.text}
                      </RNText>
                      <RNText style={styles.metaDot}>·</RNText>
                      <RNText style={styles.metaText}>{formatSize(doc.fileSize)}</RNText>
                      <RNText style={styles.metaDot}>·</RNText>
                      <RNText style={styles.metaText}>
                        {doc.uploadTime ? dayjs(doc.uploadTime).format('MM-DD HH:mm') : ''}
                      </RNText>
                    </View>
                  </View>
                  {doc.status === 'PROCESSING' ? (
                    <ActivityIndicator size={18} color={colors.info} style={{ marginLeft: 6 }} />
                  ) : null}
                </View>

                <View style={styles.docActions}>
                  <View style={styles.enabledRow}>
                    <RNText style={styles.enabledLabel}>
                      {doc.enabled ? '已启用' : '已停用'}
                    </RNText>
                    <Switch
                      value={!!doc.enabled}
                      onValueChange={() => handleToggle(doc)}
                      disabled={doc.status !== 'COMPLETED'}
                    />
                  </View>
                  <IconButton
                    icon="trash-can-outline"
                    size={20}
                    onPress={() => handleDelete(doc)}
                    iconColor={colors.danger}
                  />
                </View>
              </ModernCard>
            );
          })
        )}
      </ScrollView>

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

  summary: { marginBottom: spacing.md },
  kbTitle: { ...typography.titleLg, color: colors.textPrimary },
  kbMeta: { ...typography.bodySm, color: colors.textSecondary, marginTop: 4 },
  kbHint: { ...typography.caption, color: colors.textTertiary, marginTop: 8 },

  uploadBtn: { marginBottom: spacing.md, borderRadius: radii.md },

  emptyBox: { alignItems: 'center', paddingVertical: spacing['3xl'] },
  emptyIcon: { fontSize: 48, marginBottom: spacing.md },
  emptyTitle: { ...typography.titleMd, color: colors.textPrimary },
  emptyHint: { ...typography.bodySm, color: colors.textTertiary, marginTop: 4 },

  docCard: { marginBottom: spacing.sm },
  docHeader: { flexDirection: 'row', alignItems: 'center' },
  docName: { ...typography.titleSm, color: colors.textPrimary },
  docMeta: { flexDirection: 'row', alignItems: 'center', marginTop: 4, flexWrap: 'wrap' },
  statusChip: {
    ...typography.caption,
    fontWeight: '600',
    borderWidth: 1,
    borderRadius: radii.pill,
    paddingHorizontal: 8,
    paddingVertical: 1,
    marginRight: 6,
  },
  metaText: { ...typography.caption, color: colors.textSecondary },
  metaDot: { ...typography.caption, color: colors.textTertiary, marginHorizontal: 4 },

  docActions: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: spacing.sm,
    paddingTop: spacing.sm,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  enabledRow: { flexDirection: 'row', alignItems: 'center' },
  enabledLabel: {
    ...typography.bodySm,
    color: colors.textSecondary,
    marginRight: spacing.sm,
    fontWeight: '600',
  },
});
