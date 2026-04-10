import { useEffect, useState } from 'react';
import { Card, Table, Select, Button, Tag, Modal, Radio, Input, message, Space, Tabs, Pagination, Typography } from 'antd';
import { getQuestions, getQuestionDetail, submitAnswer, getWrongAnswers, resolveWrongAnswer, getKnowledgePoints } from '../../api/question';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

const subjectOptions = [
  { label: '全部', value: '' },
  { label: '政治', value: '政治' },
  { label: '英语', value: '英语' },
  { label: '数学', value: '数学' },
  { label: '专业课', value: '专业课' },
];

const difficultyColors = ['', 'green', 'cyan', 'blue', 'orange', 'red'];

export default function QuestionPage() {
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
      message.error('获取题目失败');
    }
  };

  const handleSubmit = async () => {
    if (!userAnswer) { message.warning('请先作答'); return; }
    try {
      const res = await submitAnswer({ questionId: currentQuestion.id, userAnswer });
      setResult(res.data);
    } catch (err) {
      message.error('提交失败');
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '科目', dataIndex: 'subject', width: 80, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '题型', dataIndex: 'type', width: 80 },
    { title: '难度', dataIndex: 'difficulty', width: 80, render: (v) => <Tag color={difficultyColors[v]}>{'★'.repeat(v)}</Tag> },
    { title: '题目', dataIndex: 'content', ellipsis: true },
    { title: '年份', dataIndex: 'year', width: 80 },
    {
      title: '操作', width: 100,
      render: (_, record) => <Button type="link" onClick={() => handlePractice(record.id)}>做题</Button>,
    },
  ];

  const wrongColumns = [
    { title: '题目ID', dataIndex: 'questionId', width: 80 },
    { title: '我的答案', dataIndex: 'userAnswer', width: 120 },
    { title: '时间', dataIndex: 'createdAt', width: 180 },
    { title: '状态', dataIndex: 'isResolved', width: 80, render: (v) => v ? <Tag color="green">已解决</Tag> : <Tag color="red">未解决</Tag> },
    {
      title: '操作', width: 160,
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => handlePractice(record.questionId)}>重做</Button>
          {!record.isResolved && (
            <Button type="link" onClick={async () => { await resolveWrongAnswer(record.id); loadWrongAnswers(); message.success('已标记'); }}>
              标记解决
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>智能题库</Title>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'bank', label: `题库 (${total})`,
          children: (
            <>
              <Space style={{ marginBottom: 16 }}>
                <span>科目：</span>
                <Select options={subjectOptions} value={subject} onChange={setSubject} style={{ width: 120 }} />
              </Space>
              <Table dataSource={questions} columns={columns} rowKey="id" pagination={{
                current: page + 1, total, pageSize: 10,
                onChange: (p) => setPage(p - 1),
              }} />
            </>
          ),
        },
        {
          key: 'wrong', label: `错题本 (${wrongTotal})`,
          children: <Table dataSource={wrongList} columns={wrongColumns} rowKey="id" pagination={false} />,
        },
      ]} />

      <Modal
        title="做题" open={practiceVisible} width={700} footer={null}
        onCancel={() => setPracticeVisible(false)}
      >
        {currentQuestion && (
          <div>
            <Tag color="blue">{currentQuestion.subject}</Tag>
            <Tag color={difficultyColors[currentQuestion.difficulty]}>难度 {'★'.repeat(currentQuestion.difficulty)}</Tag>
            <Tag>{currentQuestion.type}</Tag>
            <Paragraph style={{ marginTop: 16, fontSize: 16 }}>{currentQuestion.content}</Paragraph>

            {currentQuestion.options?.length > 0 ? (
              <Radio.Group value={userAnswer} onChange={(e) => setUserAnswer(e.target.value)} style={{ display: 'block', marginBottom: 16 }}>
                {currentQuestion.options.map((opt) => (
                  <Radio key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 8 }}>
                    {opt.label}. {opt.content}
                  </Radio>
                ))}
              </Radio.Group>
            ) : (
              <TextArea rows={3} value={userAnswer} onChange={(e) => setUserAnswer(e.target.value)} placeholder="请输入你的答案" style={{ marginBottom: 16 }} />
            )}

            {!result ? (
              <Button type="primary" onClick={handleSubmit}>提交答案</Button>
            ) : (
              <Card size="small" style={{ background: result.isCorrect ? '#f6ffed' : '#fff2f0' }}>
                <p><strong>{result.isCorrect ? '回答正确！' : '回答错误'}</strong></p>
                <p>正确答案：{result.correctAnswer}</p>
                {result.analysis && <p>解析：{result.analysis}</p>}
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
