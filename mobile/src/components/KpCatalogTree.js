import React, { useState } from 'react';
import { View, StyleSheet, TouchableOpacity } from 'react-native';
import { Text, ProgressBar } from 'react-native-paper';
import { colors, spacing } from '../theme';

const LEVEL_CONFIG = {
  strong:       { color: colors.success, label: '已掌握', dot: '🟢' },
  intermediate: { color: colors.warning, label: '学习中', dot: '🟡' },
  weak:         { color: colors.danger,  label: '薄弱',   dot: '🔴' },
  untouched:    { color: colors.textTertiary, label: '未学', dot: '⚪' },
};

function collectLeaves(node) {
  if (!node.children || node.children.length === 0) return [node];
  let all = [];
  for (const child of node.children) {
    all = all.concat(collectLeaves(child));
  }
  return all;
}

export default function KpCatalogTree({ nodes = [], onPressNode }) {
  const [expanded, setExpanded] = useState({});

  // 根节点默认全部展开
  React.useEffect(() => {
    if (nodes.length > 0) {
      const initial = {};
      nodes.forEach(n => {
        if (n.children?.length > 0) initial[n.id] = true;
      });
      setExpanded(initial);
    }
  }, [nodes]);

  const toggleExpand = (id) => {
    setExpanded(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const renderNode = (node, depth) => {
    const hasChildren = node.children?.length > 0;
    const isExpanded = expanded[node.id];
    const cfg = LEVEL_CONFIG[node.level] || LEVEL_CONFIG.untouched;
    const masteryVal = node.mastery ? Number(node.mastery) : 0;

    return (
      <View key={node.id}>
        <TouchableOpacity
          style={[styles.row, { paddingLeft: spacing.sm + depth * 20 }]}
          activeOpacity={0.6}
          onPress={() => {
            if (hasChildren) {
              toggleExpand(node.id);
            } else {
              onPressNode?.(node.id);
            }
          }}
        >
          {/* 展开/折叠箭头 */}
          {hasChildren ? (
            <Text style={styles.arrow}>{isExpanded ? '▼' : '▶'}</Text>
          ) : (
            <View style={styles.arrowPlaceholder} />
          )}

          {/* 掌握度圆点 */}
          <Text style={styles.dot}>{cfg.dot}</Text>

          {/* 知识点名称 */}
          <View style={styles.nameWrap}>
            <Text
              numberOfLines={1}
              style={[styles.name, node.level === 'untouched' && styles.nameDim]}
            >
              {node.name}
            </Text>
            {node.questionCount > 0 && masteryVal > 0 && (
              <ProgressBar
                progress={Math.min(1, masteryVal)}
                color={cfg.color}
                style={styles.miniBar}
              />
            )}
          </View>

          {/* 题目计数 */}
          {node.questionCount > 0 && (
            <Text style={[styles.count, { color: cfg.color }]}>
              {node.questionCount}题
            </Text>
          )}
          {node.questionCount === 0 && (
            <Text style={styles.countZero}>0题</Text>
          )}
        </TouchableOpacity>

        {/* 子节点 */}
        {hasChildren && isExpanded &&
          node.children.map(child => renderNode(child, depth + 1))}
      </View>
    );
  };

  // 计算全局进度
  const allLeaves = [];
  const collectAll = (list) => {
    for (const n of list) {
      if (!n.children || n.children.length === 0) {
        allLeaves.push(n);
      } else {
        collectAll(n.children);
      }
    }
  };
  collectAll(nodes);

  const totalLeaves = allLeaves.length;
  const strongCount = allLeaves.filter(n => n.level === 'strong').length;
  const intermediateCount = allLeaves.filter(n => n.level === 'intermediate').length;
  const weakCount = allLeaves.filter(n => n.level === 'weak').length;
  const untouchedCount = allLeaves.filter(n => n.level === 'untouched').length;
  const studied = totalLeaves - untouchedCount;
  const strongPercent = totalLeaves > 0 ? Math.round((strongCount / totalLeaves) * 100) : 0;
  const studiedPercent = totalLeaves > 0 ? Math.round((studied / totalLeaves) * 100) : 0;

  if (nodes.length === 0) {
    return (
      <View style={styles.emptyWrap}>
        <Text style={styles.emptyText}>暂无知识点目录</Text>
        <Text style={styles.emptySub}>请先做题或等待播下种子数据</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {nodes.map(node => renderNode(node, 0))}

      {/* 底部进度条 */}
      <View style={styles.progressBar}>
        <View style={styles.progressSegments}>
          <View style={[styles.progressSegment, {
            flex: strongCount,
            backgroundColor: colors.success,
          }]} />
          <View style={[styles.progressSegment, {
            flex: intermediateCount,
            backgroundColor: colors.warning,
          }]} />
          <View style={[styles.progressSegment, {
            flex: weakCount,
            backgroundColor: colors.danger,
          }]} />
          <View style={[styles.progressSegment, {
            flex: Math.max(1, untouchedCount),
            backgroundColor: colors.textTertiary,
          }]} />
        </View>
        <View style={styles.progressLabels}>
          <Text style={styles.progressText}>
            🟢 已掌握 {strongCount}（{strongPercent}%）  🟡 {intermediateCount}  🔴 {weakCount}  ⚪ {untouchedCount}
          </Text>
          <Text style={styles.progressSub}>
            共 {totalLeaves} 个知识点 · 已学习 {studiedPercent}%
          </Text>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 9,
    paddingRight: spacing.sm,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.borderLight,
    minHeight: 44,
  },
  arrow: {
    width: 20,
    fontSize: 10,
    color: colors.textTertiary,
    textAlign: 'center',
  },
  arrowPlaceholder: {
    width: 20,
  },
  dot: {
    fontSize: 12,
    marginRight: 6,
  },
  nameWrap: {
    flex: 1,
    marginRight: 8,
  },
  name: {
    fontSize: 14,
    color: colors.textPrimary,
  },
  nameDim: {
    color: colors.textTertiary,
  },
  miniBar: {
    height: 3,
    borderRadius: 2,
    marginTop: 3,
  },
  count: {
    fontSize: 12,
    fontWeight: '600',
    minWidth: 36,
    textAlign: 'right',
  },
  countZero: {
    fontSize: 12,
    color: colors.textTertiary,
    minWidth: 36,
    textAlign: 'right',
  },
  emptyWrap: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 40,
  },
  emptyText: {
    fontSize: 16,
    color: colors.textSecondary,
    marginBottom: 6,
  },
  emptySub: {
    fontSize: 13,
    color: colors.textTertiary,
  },
  progressBar: {
    paddingHorizontal: spacing.sm,
    paddingVertical: 10,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.surface,
  },
  progressSegments: {
    flexDirection: 'row',
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
    marginBottom: 8,
  },
  progressSegment: {
    height: '100%',
  },
  progressLabels: {
    alignItems: 'center',
  },
  progressText: {
    fontSize: 12,
    color: colors.textSecondary,
  },
  progressSub: {
    fontSize: 11,
    color: colors.textTertiary,
    marginTop: 2,
  },
});
