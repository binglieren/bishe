import { useEffect, useState } from 'react';
import { Card, Table, Button, Tag, Modal, Radio, Input, message, Space, Statistic, Row, Col, Typography, Divider } from 'antd';
import { ClockCircleOutlined, TrophyOutlined } from '@ant-design/icons';
import { getExams, getExamDetail, startExam, submitExam, getMyRecords, getExamResult } from '../../api/exam';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;
const { Countdown } = Statistic;

export default function ExamPage() {
  const [exams, setExams] = useState([]);
  const [records, setRecords] = useState([]);
  const [examVisible, setExamVisible] = useState(false);
  const [currentExam, setCurrentExam] = useState(null);
  const [examQuestions, setExamQuestions] = useState([]);
  const [answers, setAnswers] = useState({});
  const [recordId, setRecordId] = useState(null);
  const [deadline, setDeadline] = useState(null);
  const [resultVisible, setResultVisible] = useState(false);
  const [examResult, setExamResult] = useState(null);

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
      setDeadline(Date.now() + detailRes.data.exam.durationMinutes * 60 * 1000);
      setExamVisible(true);
    } catch (err) {
      message.error('开始考试失败');
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
      message.error('提交失败');
    }
  };

  const handleViewResult = async (rid) => {
    try {
      const res = await getExamResult(rid);
      setExamResult(res.data);
      setResultVisible(true);
    } catch (err) {
      message.error('获取结果失败');
    }
  };

  const examColumns = [
    { title: '考试名称', dataIndex: 'title' },
    { title: '科目', dataIndex: 'subject', width: 80, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '时长', dataIndex: 'durationMinutes', width: 100, render: (v) => `${v} 分钟` },
    { title: '总分', dataIndex: 'totalScore', width: 80 },
    {
      title: '操作', width: 120,
      render: (_, record) => <Button type="primary" size="small" onClick={() => handleStartExam(record.id)}>开始考试</Button>,
    },
  ];

  const recordColumns = [
    { title: '考试ID', dataIndex: 'examId', width: 80 },
    { title: '得分', dataIndex: 'score', width: 80, render: (v) => v ?? '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: (v) => v === 'COMPLETED' ? <Tag color="green">已完成</Tag> : <Tag color="processing">进行中</Tag> },
    { title: '时间', dataIndex: 'createdAt', width: 180 },
    {
      title: '操作', width: 100,
      render: (_, record) => record.status === 'COMPLETED' && (
        <Button type="link" onClick={() => handleViewResult(record.id)}>查看结果</Button>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>模拟考试</Title>

      <Card title="可用考试" style={{ marginBottom: 16 }}>
        <Table dataSource={exams} columns={examColumns} rowKey="id" pagination={false} />
      </Card>

      <Card title="考试记录">
        <Table dataSource={records} columns={recordColumns} rowKey="id" pagination={false} />
      </Card>

      {/* 考试弹窗 */}
      <Modal
        title={currentExam?.title} open={examVisible} width={800} closable={false}
        footer={<Button type="primary" onClick={handleSubmitExam} danger>交卷</Button>}
      >
        {deadline && (
          <div style={{ textAlign: 'right', marginBottom: 16 }}>
            <Countdown title="剩余时间" value={deadline} onFinish={handleSubmitExam} format="HH:mm:ss" />
          </div>
        )}
        {examQuestions.map((eq, idx) => (
          <Card key={eq.id} size="small" style={{ marginBottom: 12 }}>
            <p><strong>{idx + 1}. </strong>{eq.question?.content} <Tag>({eq.score}分)</Tag></p>
            {eq.question?.options?.length > 0 ? (
              <Radio.Group
                value={answers[eq.questionId]}
                onChange={(e) => setAnswers({ ...answers, [eq.questionId]: e.target.value })}
              >
                {eq.question.options.map((opt) => (
                  <Radio key={opt.label} value={opt.label} style={{ display: 'block', margin: '4px 0' }}>
                    {opt.label}. {opt.content}
                  </Radio>
                ))}
              </Radio.Group>
            ) : (
              <TextArea rows={2} value={answers[eq.questionId] || ''}
                onChange={(e) => setAnswers({ ...answers, [eq.questionId]: e.target.value })} placeholder="请输入答案" />
            )}
          </Card>
        ))}
      </Modal>

      {/* 结果弹窗 */}
      <Modal title="考试结果" open={resultVisible} onCancel={() => setResultVisible(false)} footer={null} width={600}>
        {examResult && (
          <div>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={8}><Statistic title="得分" value={examResult.totalScore ?? examResult.record?.score} suffix={`/ ${examResult.fullScore ?? ''}`} prefix={<TrophyOutlined />} /></Col>
              <Col span={8}><Statistic title="正确数" value={examResult.correctCount ?? '-'} suffix={`/ ${examResult.totalQuestions ?? ''}`} /></Col>
              <Col span={8}><Statistic title="正确率" value={examResult.accuracy != null ? examResult.accuracy.toFixed(1) : '-'} suffix="%" /></Col>
            </Row>
          </div>
        )}
      </Modal>
    </div>
  );
}
