import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView, Modal, FlatList } from 'react-native';
import { Text, Card, Button, RadioButton, TextInput, Chip, Snackbar } from 'react-native-paper';
import { getExams, getExamDetail, startExam, submitExam, getMyRecords, getExamResult } from '../api/exam';

export default function ExamScreen() {
  const [exams, setExams] = useState([]);
  const [records, setRecords] = useState([]);
  const [examVisible, setExamVisible] = useState(false);
  const [currentExam, setCurrentExam] = useState(null);
  const [examQuestions, setExamQuestions] = useState([]);
  const [answers, setAnswers] = useState({});
  const [recordId, setRecordId] = useState(null);
  const [resultVisible, setResultVisible] = useState(false);
  const [examResult, setExamResult] = useState(null);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const loadData = async () => {
    try {
      const [examsRes, recordsRes] = await Promise.all([getExams(), getMyRecords()]);
      setExams(examsRes.data || []);
      setRecords(recordsRes.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadData(); }, []);

  const handleStartExam = async (examId) => {
    try {
      const detailRes = await getExamDetail(examId);
      const record = await startExam(examId);
      setCurrentExam(detailRes.data.exam);
      setExamQuestions(detailRes.data.questions || []);
      setRecordId(record.data.id);
      setAnswers({});
      setExamVisible(true);
    } catch (err) {
      setSnackMsg('开始考试失败');
      setSnackVisible(true);
    }
  };

  const handleSubmitExam = async () => {
    const answerList = examQuestions.map((eq) => ({
      questionId: eq.questionId,
      userAnswer: answers[eq.questionId] || '',
    }));
    try {
      const res = await submitExam({ recordId, answers: answerList });
      setExamVisible(false);
      setExamResult(res.data);
      setResultVisible(true);
      loadData();
    } catch (err) {
      setSnackMsg('提交失败');
      setSnackVisible(true);
    }
  };

  const handleViewResult = async (rid) => {
    try {
      const res = await getExamResult(rid);
      setExamResult(res.data);
      setResultVisible(true);
    } catch (err) {
      setSnackMsg('获取结果失败');
      setSnackVisible(true);
    }
  };

  const renderExam = ({ item }) => (
    <Card style={styles.card}>
      <Card.Content>
        <Text style={styles.examTitle}>{item.title}</Text>
        <View style={styles.row}>
          <Chip mode="outlined" style={styles.chip}>{item.subject}</Chip>
          <Text style={styles.info}>{item.durationMinutes} 分钟</Text>
          <Text style={styles.info}>总分 {item.totalScore}</Text>
        </View>
      </Card.Content>
      <Card.Actions>
        <Button mode="contained" onPress={() => handleStartExam(item.id)}>开始考试</Button>
      </Card.Actions>
    </Card>
  );

  return (
    <View style={styles.container}>
      <ScrollView>
        <Text style={styles.sectionTitle}>可用考试</Text>
        {exams.map((item) => (
          <View key={item.id}>{renderExam({ item })}</View>
        ))}

        <Text style={styles.sectionTitle}>考试记录</Text>
        {records.map((item) => (
          <Card key={item.id} style={styles.card}>
            <Card.Content>
              <View style={styles.row}>
                <Text style={styles.info}>考试ID: {item.examId}</Text>
                <Chip mode="outlined" textStyle={{ color: item.status === 'COMPLETED' ? '#52c41a' : '#1677ff' }}>
                  {item.status === 'COMPLETED' ? '已完成' : '进行中'}
                </Chip>
              </View>
              <Text style={styles.info}>得分: {item.score ?? '-'} | 时间: {item.createdAt}</Text>
            </Card.Content>
            <Card.Actions>
              {item.status === 'COMPLETED' && (
                <Button mode="text" onPress={() => handleViewResult(item.id)}>查看结果</Button>
              )}
            </Card.Actions>
          </Card>
        ))}
      </ScrollView>

      <Modal visible={examVisible} animationType="slide">
        <ScrollView style={styles.modal}>
          <Text style={styles.examTitle}>{currentExam?.title}</Text>
          {examQuestions.map((eq, idx) => (
            <Card key={eq.id} style={styles.questionCard}>
              <Card.Content>
                <View style={styles.row}>
                  <Text style={styles.questionText}>{idx + 1}. {eq.question?.content}</Text>
                  <Chip mode="outlined">{eq.score}分</Chip>
                </View>
                {eq.question?.options?.length > 0 ? (
                  <RadioButton.Group
                    value={answers[eq.questionId]}
                    onValueChange={(v) => setAnswers({ ...answers, [eq.questionId]: v })}
                  >
                    {eq.question.options.map((opt) => (
                      <RadioButton.Item
                        key={opt.label}
                        label={`${opt.label}. ${opt.content}`}
                        value={opt.label}
                      />
                    ))}
                  </RadioButton.Group>
                ) : (
                  <TextInput
                    mode="outlined"
                    placeholder="请输入答案"
                    value={answers[eq.questionId] || ''}
                    onChangeText={(v) => setAnswers({ ...answers, [eq.questionId]: v })}
                  />
                )}
              </Card.Content>
            </Card>
          ))}
          <Button mode="contained" onPress={handleSubmitExam} style={styles.submitBtn}>交卷</Button>
          <Button mode="text" onPress={() => setExamVisible(false)} style={styles.cancelBtn}>取消</Button>
        </ScrollView>
      </Modal>

      <Modal visible={resultVisible} animationType="slide">
        <View style={styles.resultModal}>
          <Text style={styles.sectionTitle}>考试结果</Text>
          {examResult && (
            <View style={styles.resultRow}>
              <Card style={styles.resultCard}>
                <Card.Content style={styles.resultContent}>
                  <Text style={styles.resultLabel}>得分</Text>
                  <Text style={styles.resultValue}>
                    {examResult.totalScore ?? examResult.record?.score}/{examResult.fullScore ?? ''}
                  </Text>
                </Card.Content>
              </Card>
              <Card style={styles.resultCard}>
                <Card.Content style={styles.resultContent}>
                  <Text style={styles.resultLabel}>正确数</Text>
                  <Text style={styles.resultValue}>
                    {examResult.correctCount ?? '-'}/{examResult.totalQuestions ?? ''}
                  </Text>
                </Card.Content>
              </Card>
              <Card style={styles.resultCard}>
                <Card.Content style={styles.resultContent}>
                  <Text style={styles.resultLabel}>正确率</Text>
                  <Text style={styles.resultValue}>
                    {examResult.accuracy != null ? examResult.accuracy.toFixed(1) : '-'}%
                  </Text>
                </Card.Content>
              </Card>
            </View>
          )}
          <Button mode="contained" onPress={() => setResultVisible(false)} style={styles.closeBtn}>关闭</Button>
        </View>
      </Modal>

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    marginVertical: 12,
    marginHorizontal: 12,
  },
  card: {
    marginHorizontal: 12,
    marginBottom: 10,
  },
  examTitle: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap',
  },
  chip: {
    height: 30,
  },
  info: {
    fontSize: 13,
    color: '#666',
  },
  modal: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
    paddingTop: 60,
  },
  questionCard: {
    marginBottom: 12,
  },
  questionText: {
    fontSize: 15,
    flex: 1,
    lineHeight: 22,
  },
  submitBtn: {
    marginTop: 16,
    marginHorizontal: 16,
  },
  cancelBtn: {
    marginTop: 8,
    marginHorizontal: 16,
    marginBottom: 40,
  },
  resultModal: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 16,
    paddingTop: 80,
  },
  resultRow: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 16,
  },
  resultCard: {
    flex: 1,
  },
  resultContent: {
    alignItems: 'center',
  },
  resultLabel: {
    fontSize: 13,
    color: '#999',
  },
  resultValue: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#1677ff',
  },
  closeBtn: {
    marginTop: 24,
  },
});