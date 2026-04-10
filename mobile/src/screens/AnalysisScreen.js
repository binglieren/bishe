import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, Card, Button, Chip, ProgressBar, Snackbar } from 'react-native-paper';
import { getMasteryOverview, getWeakPoints, getRecommendedQuestions } from '../api/analysis';

export default function AnalysisScreen() {
  const [mastery, setMastery] = useState([]);
  const [weakPoints, setWeakPoints] = useState([]);
  const [recommended, setRecommended] = useState([]);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const loadData = async () => {
    try {
      const [masteryRes, weakRes] = await Promise.all([
        getMasteryOverview(),
        getWeakPoints(60),
      ]);
      setMastery(masteryRes.data || []);
      setWeakPoints(weakRes.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  const loadRecommend = async () => {
    try {
      const res = await getRecommendedQuestions(10);
      setRecommended(res.data || []);
      if (res.data?.length === 0) {
        setSnackMsg('暂无推荐题目');
        setSnackVisible(true);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadData(); }, []);

  const avgMastery = mastery.length > 0
    ? (mastery.reduce((sum, m) => sum + parseFloat(m.masteryLevel), 0) / mastery.length).toFixed(1)
    : 0;

  const getProgressColor = (val) => {
    const v = parseFloat(val);
    if (v >= 80) return '#52c41a';
    if (v >= 60) return '#1677ff';
    if (v >= 40) return '#faad14';
    return '#f5222d';
  };

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contentContainer}>
      <View style={styles.statsRow}>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>已学知识点</Text>
            <Text style={styles.statValue}>{mastery.length}</Text>
          </Card.Content>
        </Card>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>薄弱知识点</Text>
            <Text style={[styles.statValue, { color: weakPoints.length > 0 ? '#cf1322' : '#52c41a' }]}>
              {weakPoints.length}
            </Text>
          </Card.Content>
        </Card>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>平均掌握度</Text>
            <Text style={styles.statValue}>{avgMastery}%</Text>
          </Card.Content>
        </Card>
      </View>

      <Card style={styles.card}>
        <Card.Title title="薄弱知识点（掌握度 < 60%）" titleStyle={styles.cardTitle} />
        <Card.Content>
          {weakPoints.length === 0 ? (
            <Text style={styles.emptyText}>暂无薄弱知识点，继续保持！</Text>
          ) : (
            weakPoints.map((item) => (
              <View key={item.knowledgePointId} style={styles.weakItem}>
                <View style={styles.weakRow}>
                  <Chip mode="outlined" style={styles.chip}>{item.subject}</Chip>
                  <Text style={styles.weakName}>{item.knowledgePointName}</Text>
                </View>
                <View style={styles.weakRow}>
                  <Text style={styles.weakRate}>
                    正确率: {parseFloat(item.masteryLevel).toFixed(1)}%
                  </Text>
                  <Text style={styles.weakCount}>做题 {item.totalCount} 次</Text>
                </View>
                <ProgressBar
                  progress={parseFloat(item.masteryLevel) / 100}
                  color={getProgressColor(item.masteryLevel)}
                  style={styles.progressBar}
                />
              </View>
            ))
          )}
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title
          title="推荐练习"
          right={() => (
            <Button mode="contained" onPress={loadRecommend} style={{ marginRight: 12 }}>
              获取推荐题目
            </Button>
          )}
        />
        <Card.Content>
          {recommended.length > 0 ? (
            recommended.map((item) => (
              <View key={item.id} style={styles.recommendItem}>
                <View style={styles.recommendRow}>
                  <Chip mode="outlined" style={styles.chip}>{item.subject}</Chip>
                  <Chip mode="outlined">{item.type}</Chip>
                  <Text style={styles.difficulty}>{'★'.repeat(item.difficulty)}</Text>
                </View>
                <Text style={styles.recommendContent} numberOfLines={2}>{item.content}</Text>
              </View>
            ))
          ) : (
            <Text style={styles.emptyText}>点击"获取推荐题目"，系统将根据薄弱知识点推荐练习</Text>
          )}
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title title="所有知识点掌握度" />
        <Card.Content>
          {mastery.length === 0 ? (
            <Text style={styles.emptyText}>暂无数据，开始做题后将在这里展示各知识点掌握度</Text>
          ) : (
            mastery.map((item) => (
              <View key={item.knowledgePointId} style={styles.masteryItem}>
                <View style={styles.masteryRow}>
                  <Chip mode="outlined" style={styles.chip}>{item.subject}</Chip>
                  <Text style={styles.masteryName}>{item.knowledgePointName}</Text>
                </View>
                <View style={styles.masteryRow}>
                  <Text style={styles.masteryInfo}>
                    做题 {item.totalCount} 次 | 正确 {item.correctCount} 次
                  </Text>
                  <Text style={styles.masteryPercent}>{parseFloat(item.masteryLevel).toFixed(1)}%</Text>
                </View>
                <ProgressBar
                  progress={parseFloat(item.masteryLevel) / 100}
                  color={getProgressColor(item.masteryLevel)}
                  style={styles.progressBar}
                />
              </View>
            ))
          )}
        </Card.Content>
      </Card>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  contentContainer: {
    padding: 12,
    paddingBottom: 24,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 12,
  },
  statCard: {
    flex: 1,
  },
  statContent: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 12,
    color: '#999',
  },
  statValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1677ff',
  },
  card: {
    marginBottom: 12,
  },
  cardTitle: {
    color: '#cf1322',
  },
  emptyText: {
    color: '#999',
    textAlign: 'center',
    paddingVertical: 16,
  },
  weakItem: {
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  weakRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  chip: {
    height: 30,
  },
  weakName: {
    fontSize: 15,
    fontWeight: '500',
  },
  weakRate: {
    fontSize: 13,
    color: '#f5222d',
  },
  weakCount: {
    fontSize: 13,
    color: '#999',
  },
  progressBar: {
    height: 6,
    borderRadius: 3,
    marginTop: 4,
  },
  recommendItem: {
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  recommendRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    marginBottom: 4,
  },
  difficulty: {
    color: '#fa8c16',
    fontSize: 12,
  },
  recommendContent: {
    fontSize: 14,
    color: '#333',
  },
  masteryItem: {
    marginBottom: 12,
    paddingBottom: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#eee',
  },
  masteryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 4,
  },
  masteryName: {
    fontSize: 14,
    fontWeight: '500',
  },
  masteryInfo: {
    fontSize: 12,
    color: '#999',
  },
  masteryPercent: {
    fontSize: 14,
    fontWeight: '600',
    color: '#1677ff',
  },
});