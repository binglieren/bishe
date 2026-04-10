import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Text, Card, Button, Chip, Snackbar } from 'react-native-paper';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getUserInfo, checkIn } from '../api/auth';
import { getSubjectAnalysis } from '../api/analysis';
import dayjs from 'dayjs';

export default function DashboardScreen({ navigation }) {
  const [userInfo, setUserInfo] = useState({});
  const [subjectStats, setSubjectStats] = useState({});
  const [refreshing, setRefreshing] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

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

  const handleCheckIn = async () => {
    try {
      await checkIn(0);
      setSnackMsg('打卡成功！');
      setSnackVisible(true);
      loadData();
    } catch (err) {
      setSnackMsg(err.message || '打卡失败');
      setSnackVisible(true);
    }
  };

  const handleLogout = async () => {
    await AsyncStorage.removeItem('token');
    await AsyncStorage.removeItem('username');
    navigation.replace('Login');
  };

  React.useLayoutEffect(() => {
    navigation.setOptions({
      headerRight: () => (
        <Button
          textColor="#fff"
          onPress={handleLogout}
          icon="logout"
          compact
        >
          退出
        </Button>
      ),
    });
  }, [navigation]);

  const daysUntilExam = userInfo.examDate
    ? dayjs(userInfo.examDate).diff(dayjs(), 'day')
    : null;

  const subjectEntries = Object.entries(subjectStats);

  return (
    <ScrollView
      style={styles.container}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
    >
      <View style={styles.statsRow}>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>累计打卡</Text>
            <Text style={styles.statValue}>{userInfo.checkInDays || 0}</Text>
            <Text style={styles.statUnit}>天</Text>
          </Card.Content>
        </Card>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>学习时长</Text>
            <Text style={styles.statValue}>{userInfo.totalStudyMinutes || 0}</Text>
            <Text style={styles.statUnit}>分钟</Text>
          </Card.Content>
        </Card>
      </View>

      <View style={styles.statsRow}>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>目标院校</Text>
            <Text style={styles.statSub}>{userInfo.targetSchool || '未设置'}</Text>
          </Card.Content>
        </Card>
        <Card style={styles.statCard}>
          <Card.Content style={styles.statContent}>
            <Text style={styles.statLabel}>距离考试</Text>
            <Text style={[styles.statValue, daysUntilExam != null && daysUntilExam < 30 && styles.danger]}>
              {daysUntilExam != null ? daysUntilExam : '--'}
            </Text>
            <Text style={styles.statUnit}>{daysUntilExam != null ? '天' : ''}</Text>
          </Card.Content>
        </Card>
      </View>

      <Card style={styles.card}>
        <Card.Title title="今日打卡" right={() => (
          <Button mode="contained" onPress={handleCheckIn} style={styles.checkBtn}>
            打卡
          </Button>
        )} />
        <Card.Content>
          <Text style={styles.infoRow}>目标专业：{userInfo.targetMajor || '未设置'}</Text>
          <Text style={styles.infoRow}>考试日期：{userInfo.examDate || '未设置'}</Text>
          <Text style={styles.infoRow}>开始备考：{userInfo.studyStartDate || '未设置'}</Text>
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title title="各科掌握情况" />
        <Card.Content>
          {subjectEntries.length === 0 ? (
            <Text style={styles.emptyText}>暂无做题数据，开始做题后这里将显示各科掌握度</Text>
          ) : (
            subjectEntries.map(([subject, stats]) => (
              <View key={subject} style={styles.subjectRow}>
                <Chip mode="outlined" style={styles.chip}>{subject}</Chip>
                <Text style={styles.subjectInfo}>
                  正确率: {stats.averageMastery}% | 做题 {stats.totalAttempts} 次 | 知识点 {stats.knowledgePointCount} 个
                </Text>
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
    padding: 12,
  },
  statsRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 12,
  },
  statCard: {
    flex: 1,
  },
  statContent: {
    alignItems: 'center',
  },
  statLabel: {
    fontSize: 13,
    color: '#666',
  },
  statValue: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#1677ff',
  },
  statSub: {
    fontSize: 16,
    fontWeight: '600',
    color: '#333',
  },
  statUnit: {
    fontSize: 12,
    color: '#999',
  },
  danger: {
    color: '#cf1322',
  },
  card: {
    marginBottom: 12,
  },
  checkBtn: {
    marginRight: 12,
  },
  infoRow: {
    fontSize: 15,
    marginBottom: 6,
    color: '#333',
  },
  emptyText: {
    color: '#999',
    textAlign: 'center',
    paddingVertical: 20,
  },
  subjectRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
    gap: 8,
  },
  chip: {
    height: 32,
  },
  subjectInfo: {
    fontSize: 13,
    color: '#555',
    flex: 1,
  },
});