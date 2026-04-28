import React, { useCallback, useState } from 'react';
import {
  View,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  Text as RNText,
  Alert,
  Platform,
} from 'react-native';
import { Text, Snackbar, IconButton, FAB } from 'react-native-paper';
import { useFocusEffect } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import AsyncStorage from '@react-native-async-storage/async-storage';
import dayjs from 'dayjs';
import { getSessions, deleteSession, createSession } from '../api/chat';
import { colors, radii, spacing, shadows, typography } from '../theme';

/**
 * 会话"已读"标记：用 AsyncStorage 持久化每个 session 上次进入时的 messageCount。
 * key 形如 chat.sessionSeen.<sessionId> -> 数字字符串
 *
 * 未读判断：item.messageCount > seenCount 时才显示徽标
 */
const SEEN_KEY_PREFIX = 'chat.sessionSeen.';
const seenKey = (sessionId) => SEEN_KEY_PREFIX + sessionId;

const formatRelative = (iso) => {
  if (!iso) return '';
  const d = dayjs(iso);
  const now = dayjs();
  if (d.isSame(now, 'day')) return d.format('HH:mm');
  if (d.isSame(now.subtract(1, 'day'), 'day')) return '昨天';
  if (d.isSame(now, 'year')) return d.format('MM-DD');
  return d.format('YYYY-MM-DD');
};

// 根据标题字符哈希决定头像底色，保持稳定
const AVATAR_PALETTE = [
  '#4F46E5', '#8B5CF6', '#10B981', '#F59E0B',
  '#EF4444', '#06B6D4', '#EC4899', '#3B82F6',
];
const avatarColorFor = (s) => {
  const str = s || '?';
  let h = 0;
  for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
  return AVATAR_PALETTE[h % AVATAR_PALETTE.length];
};
const avatarTextFor = (s) => {
  if (!s) return '对';
  const first = s.trim().charAt(0);
  return first || '对';
};

export default function ChatListScreen({ navigation }) {
  const insets = useSafeAreaInsets();
  const [sessions, setSessions] = useState([]);
  const [refreshing, setRefreshing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  // 每个会话上次进入时的 messageCount 快照（id -> 数字）
  const [seenCounts, setSeenCounts] = useState({});

  // 拉所有会话已读快照到内存
  const loadSeenCounts = useCallback(async (sessionList) => {
    if (!sessionList || sessionList.length === 0) return;
    try {
      const keys = sessionList.map((s) => seenKey(s.id));
      const pairs = await AsyncStorage.multiGet(keys);
      const map = {};
      pairs.forEach(([k, v]) => {
        if (v != null) {
          const id = k.substring(SEEN_KEY_PREFIX.length);
          const num = Number(v);
          if (!Number.isNaN(num)) map[id] = num;
        }
      });
      setSeenCounts(map);
    } catch {
      // 读不到就当全是未读，不影响主流程
    }
  }, []);

  const load = useCallback(async () => {
    try {
      const res = await getSessions();
      const list = res.data || [];
      setSessions(list);
      loadSeenCounts(list);
    } catch (err) {
      setSnackMsg('加载会话失败');
      setSnackVisible(true);
    }
  }, [loadSeenCounts]);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load])
  );

  const onRefresh = async () => {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  };

  const openChat = (session) => {
    // 进入会话前，把当前 messageCount 记为"已读分水岭"——
    // 后续没新增消息时，徽标就不再出现
    if (session?.id != null) {
      const current = Number(session.messageCount || 0);
      AsyncStorage.setItem(seenKey(session.id), String(current)).catch(() => {});
      // 立刻在内存里同步，避免 useFocusEffect 回来前徽标还残留
      setSeenCounts((prev) => ({ ...prev, [session.id]: current }));
    }
    navigation.navigate('ChatDetail', {
      sessionId: session?.id || null,
      title: session?.title || '新对话',
      knowledgeBaseId: session?.knowledgeBaseId ?? null,
    });
  };

  const handleNew = async () => {
    setLoading(true);
    try {
      // 先不落库，进入详情页后用户发送第一条消息时自动创建
      navigation.navigate('ChatDetail', { sessionId: null, title: '新对话' });
    } catch (err) {
      setSnackMsg('创建失败');
      setSnackVisible(true);
    } finally {
      setLoading(false);
    }
  };

  const confirmDelete = (session) => {
    if (Platform.OS === 'web') {
      if (window.confirm(`删除对话"${session.title}"？此操作不可撤销`)) {
        doDelete(session.id);
      }
      return;
    }
    Alert.alert(
      '删除对话',
      `确定要删除 "${session.title}" 吗？`,
      [
        { text: '取消', style: 'cancel' },
        { text: '删除', style: 'destructive', onPress: () => doDelete(session.id) },
      ]
    );
  };

  const doDelete = async (id) => {
    try {
      await deleteSession(id);
      // 顺带清掉这条会话的"已读"快照，避免 AsyncStorage 长期累积
      AsyncStorage.removeItem(seenKey(id)).catch(() => {});
      setSeenCounts((prev) => {
        const next = { ...prev };
        delete next[id];
        return next;
      });
      setSnackMsg('已删除');
      setSnackVisible(true);
      load();
    } catch (err) {
      setSnackMsg('删除失败');
      setSnackVisible(true);
    }
  };

  const renderItem = ({ item }) => {
    const preview = item.lastMessagePreview
      ? (item.lastMessageRole === 'user' ? '我：' : 'AI：') + item.lastMessagePreview
      : '还没有消息，点击开始对话';
    const color = avatarColorFor(item.title);
    const initial = avatarTextFor(item.title);
    // 真未读数 = 当前总消息数 - 上次进入时已经看到的数
    const seen = seenCounts[item.id] ?? 0;
    const unread = Math.max(0, (item.messageCount || 0) - seen);

    return (
      <TouchableOpacity
        style={styles.item}
        onPress={() => openChat(item)}
        onLongPress={() => confirmDelete(item)}
        activeOpacity={0.6}
      >
        <View style={[styles.avatar, { backgroundColor: color }]}>
          <Text style={styles.avatarText}>{initial}</Text>
        </View>

        <View style={styles.itemBody}>
          <View style={styles.itemHead}>
            <Text style={styles.itemTitle} numberOfLines={1}>{item.title || '新对话'}</Text>
            <Text style={styles.itemTime}>{formatRelative(item.updatedAt)}</Text>
          </View>
          <View style={styles.itemMetaRow}>
            <Text style={styles.itemPreview} numberOfLines={1}>{preview}</Text>
            {unread > 0 && (
              <View style={styles.countBadge}>
                <Text style={styles.countBadgeText}>
                  {unread > 99 ? '99+' : unread}
                </Text>
              </View>
            )}
          </View>
        </View>

        <IconButton
          icon="delete-outline"
          size={18}
          iconColor={colors.textTertiary}
          onPress={() => confirmDelete(item)}
          style={styles.deleteIcon}
        />
      </TouchableOpacity>
    );
  };

  return (
    <View style={[styles.container, { paddingTop: insets.top }]}>
      <FlatList
        data={sessions}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderItem}
        refreshControl={
          <RefreshControl
            refreshing={refreshing}
            onRefresh={onRefresh}
            tintColor={colors.primary}
          />
        }
        ItemSeparatorComponent={() => <View style={styles.separator} />}
        ListEmptyComponent={
          <View style={styles.empty}>
            <RNText style={styles.emptyIcon}>🤖</RNText>
            <Text style={styles.emptyText}>还没有对话</Text>
            <Text style={styles.emptyHint}>
              点击右下角 + 开启一段新对话，支持拍照搜题
            </Text>
          </View>
        }
        contentContainerStyle={sessions.length === 0 ? styles.emptyContainer : null}
      />

      <FAB
        icon="plus"
        label="新对话"
        onPress={handleNew}
        loading={loading}
        style={styles.fab}
        color="#FFFFFF"
        customSize={56}
      />

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },

  item: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: spacing.lg,
    backgroundColor: colors.surface,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: radii.md,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: spacing.md,
  },
  avatarText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: '700',
  },
  itemBody: {
    flex: 1,
    marginRight: spacing.sm,
  },
  itemHead: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 3,
  },
  itemTitle: {
    flex: 1,
    ...typography.titleSm,
    color: colors.textPrimary,
    marginRight: spacing.sm,
  },
  itemTime: {
    ...typography.caption,
    color: colors.textTertiary,
  },
  itemMetaRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  itemPreview: {
    flex: 1,
    ...typography.bodySm,
    color: colors.textSecondary,
    marginRight: spacing.sm,
  },
  countBadge: {
    backgroundColor: colors.primarySoft,
    borderRadius: radii.pill,
    paddingHorizontal: 8,
    paddingVertical: 1,
  },
  countBadgeText: {
    ...typography.caption,
    color: colors.primaryDark,
    fontWeight: '700',
    fontSize: 10,
  },
  deleteIcon: {
    margin: 0,
  },
  separator: {
    height: 1,
    backgroundColor: colors.borderLight,
    marginLeft: spacing.lg + 48 + spacing.md,
  },

  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: spacing['2xl'],
  },
  emptyContainer: {
    flexGrow: 1,
  },
  emptyIcon: {
    fontSize: 64,
    marginBottom: spacing.md,
  },
  emptyText: {
    ...typography.titleMd,
    color: colors.textSecondary,
  },
  emptyHint: {
    ...typography.bodySm,
    color: colors.textTertiary,
    textAlign: 'center',
    marginTop: spacing.xs,
    paddingHorizontal: spacing.xl,
  },

  fab: {
    position: 'absolute',
    right: spacing.lg,
    bottom: spacing.lg,
    backgroundColor: colors.primary,
    borderRadius: radii.pill,
    ...shadows.colored,
  },
});
