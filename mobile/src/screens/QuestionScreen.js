import React, { useEffect, useState } from 'react';
import { View, StyleSheet, ScrollView, FlatList } from 'react-native';
import { Text, Card, Button, RadioButton, Chip, Divider, Snackbar, FAB, SegmentedButtons } from 'react-native-paper';
import { getQuestions, getQuestionDetail, submitAnswer, getWrongAnswers, resolveWrongAnswer } from '../api/question';

const subjectOptions = [
  { label: '全部', value: '' },
  { label: '政治', value: '政治' },
  { label: '英语', value: '英语' },
  { label: '数学', value: '数学' },
  { label: '专业课', value: '专业课' },
];

const difficultyStars = (n) => '★'.repeat(n);

export default function QuestionScreen() {
  const [questions, setQuestions] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [subject, setSubject] = useState('');
  const [practiceVisible, setPracticeVisible] = useState(false);
  const [currentQuestion, setCurrentQuestion] = useState(null);
  const [userAnswer, setUserAnswer] = useState('');
  const [result, setResult] = useState(null);
  const [wrongList, setWrongList] = useState([]);
  const [wrongTotal, setWrongTotal] = useState(0);
  const [activeTab, setActiveTab] = useState('bank');
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const loadQuestions = async () => {
    try {
      const params = { page, size: 10 };
      if (subject) params.subject = subject;
      const res = await getQuestions(params);
      setQuestions(res.data.content || []);
      setTotal(res.data.totalElements || 0);
    } catch (err) {
      console.error(err);
    }
  };

  const loadWrongAnswers = async () => {
    try {
      const res = await getWrongAnswers({ page: 0, size: 50 });
      setWrongList(res.data.content || []);
      setWrongTotal(res.data.totalElements || 0);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadQuestions(); }, [page, subject]);
  useEffect(() => { if (activeTab === 'wrong') loadWrongAnswers(); }, [activeTab]);

  const handlePractice = async (id) => {
    try {
      const res = await getQuestionDetail(id);
      setCurrentQuestion(res.data);
      setUserAnswer('');
      setResult(null);
      setPracticeVisible(true);
    } catch (err) {
      setSnackMsg('获取题目失败');
      setSnackVisible(true);
    }
  };

  const handleSubmit = async () => {
    if (!userAnswer) {
      setSnackMsg('请先作答');
      setSnackVisible(true);
      return;
    }
    try {
      const res = await submitAnswer({ questionId: currentQuestion.id, userAnswer });
      setResult(res.data);
    } catch (err) {
      setSnackMsg('提交失败');
      setSnackVisible(true);
    }
  };

  const handleResolveWrong = async (id) => {
    try {
      await resolveWrongAnswer(id);
      loadWrongAnswers();
      setSnackMsg('已标记解决');
      setSnackVisible(true);
    } catch (err) {
      setSnackMsg('操作失败');
      setSnackVisible(true);
    }
  };

  const renderQuestion = ({ item }) => (
    <Card style={styles.card}>
      <Card.Content>
        <View style={styles.row}>
          <Chip mode="outlined" style={styles.chip}>ID: {item.id}</Chip>
          <Chip mode="outlined" style={styles.chip}>{item.subject}</Chip>
          <Chip mode="outlined" style={styles.chip}>{item.type}</Chip>
          <Chip mode="outlined" textStyle={styles.difficultyText}>{difficultyStars(item.difficulty)}</Chip>
        </View>
        <Text style={styles.content} numberOfLines={2}>{item.content}</Text>
        {item.year && <Text style={styles.year}>{item.year}</Text>}
      </Card.Content>
      <Card.Actions>
        <Button mode="contained" onPress={() => handlePractice(item.id)}>做题</Button>
      </Card.Actions>
    </Card>
  );

  const renderWrongItem = ({ item }) => (
    <Card style={styles.card}>
      <Card.Content>
        <View style={styles.row}>
          <Chip mode="outlined">题目ID: {item.questionId}</Chip>
          <Chip mode="outlined" textStyle={{ color: item.isResolved ? '#52c41a' : '#f5222d' }}>
            {item.isResolved ? '已解决' : '未解决'}
          </Chip>
        </View>
        <Text style={styles.content}>我的答案: {item.userAnswer}</Text>
      </Card.Content>
      <Card.Actions>
        <Button onPress={() => handlePractice(item.questionId)}>重做</Button>
        {!item.isResolved && (
          <Button mode="contained" onPress={() => handleResolveWrong(item.id)}>标记解决</Button>
        )}
      </Card.Actions>
    </Card>
  );

  const renderPracticeModal = () => {
    if (!practiceVisible || !currentQuestion) return null;
    return (
      <ScrollView style={styles.modal}>
        <View style={styles.row}>
          <Chip mode="outlined">{currentQuestion.subject}</Chip>
          <Chip mode="outlined">难度 {difficultyStars(currentQuestion.difficulty)}</Chip>
          <Chip mode="outlined">{currentQuestion.type}</Chip>
        </View>
        <Text style={styles.practiceContent}>{currentQuestion.content}</Text>

        {currentQuestion.options?.length > 0 ? (
          <RadioButton.Group
            value={userAnswer}
            onValueChange={setUserAnswer}
          >
            {currentQuestion.options.map((opt) => (
              <RadioButton.Item
                key={opt.label}
                label={`${opt.label}. ${opt.content}`}
                value={opt.label}
                style={styles.radioItem}
              />
            ))}
          </RadioButton.Group>
        ) : (
          <Text>待输入答案（需 TextInput）</Text>
        )}

        {!result ? (
          <Button mode="contained" onPress={handleSubmit} style={styles.submitBtn}>提交答案</Button>
        ) : (
          <Card style={[styles.resultCard, { backgroundColor: result.isCorrect ? '#f6ffed' : '#fff2f0' }]}>
            <Card.Content>
              <Text style={{ fontWeight: 'bold' }}>
                {result.isCorrect ? '回答正确！' : '回答错误'}
              </Text>
              <Text>正确答案：{result.correctAnswer}</Text>
              {result.analysis && <Text>解析：{result.analysis}</Text>}
            </Card.Content>
          </Card>
        )}

        <Button mode="text" onPress={() => setPracticeVisible(false)} style={styles.closeBtn}>关闭</Button>
      </ScrollView>
    );
  };

  return (
    <View style={styles.container}>
      <SegmentedButtons
        value={activeTab}
        onValueChange={setActiveTab}
        buttons={[
          { value: 'bank', label: `题库 (${total})` },
          { value: 'wrong', label: `错题本 (${wrongTotal})` },
        ]}
        style={styles.tabs}
      />

      {activeTab === 'bank' ? (
        <>
          <View style={styles.filterRow}>
            <SegmentedButtons
              value={subject}
              onValueChange={(v) => { setSubject(v); setPage(0); }}
              buttons={subjectOptions.map((o) => ({ value: o.value, label: o.label }))}
            />
          </View>
          <FlatList
            data={questions}
            keyExtractor={(item) => String(item.id)}
            renderItem={renderQuestion}
            contentContainerStyle={styles.list}
          />
        </>
      ) : (
        <FlatList
          data={wrongList}
          keyExtractor={(item) => String(item.id)}
          renderItem={renderWrongItem}
          contentContainerStyle={styles.list}
        />
      )}

      {practiceVisible && renderPracticeModal()}

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
  tabs: {
    marginHorizontal: 12,
    marginTop: 8,
  },
  filterRow: {
    paddingHorizontal: 12,
    marginTop: 8,
  },
  list: {
    padding: 12,
  },
  card: {
    marginBottom: 10,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
    marginBottom: 8,
  },
  chip: {
    height: 30,
  },
  difficultyText: {
    color: '#fa8c16',
  },
  content: {
    fontSize: 15,
    color: '#333',
    marginVertical: 4,
  },
  year: {
    fontSize: 12,
    color: '#999',
  },
  practiceContent: {
    fontSize: 16,
    marginVertical: 12,
    lineHeight: 24,
  },
  radioItem: {
    marginVertical: 4,
  },
  submitBtn: {
    marginTop: 16,
  },
  resultCard: {
    marginTop: 16,
  },
  closeBtn: {
    marginTop: 8,
  },
  modal: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: '#fff',
    padding: 16,
    zIndex: 10,
  },
});