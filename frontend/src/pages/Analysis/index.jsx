import { useEffect, useState } from 'react';
import { Card, Table, Tag, Progress, Button, Row, Col, Statistic, Typography, message } from 'antd';
import { WarningOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { getMasteryOverview, getWeakPoints, getRecommendedQuestions } from '../../api/analysis';

const { Title } = Typography;

export default function AnalysisPage() {
  const [mastery, setMastery] = useState([]);
  const [weakPoints, setWeakPoints] = useState([]);
  const [recommended, setRecommended] = useState([]);

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
      if (res.data?.length === 0) message.info('暂无推荐题目');
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadData(); }, []);

  const masteryColumns = [
    { title: '知识点', dataIndex: 'knowledgePointName' },
    { title: '科目', dataIndex: 'subject', width: 80, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '做题次数', dataIndex: 'totalCount', width: 100 },
    { title: '正确次数', dataIndex: 'correctCount', width: 100 },
    {
      title: '掌握度', dataIndex: 'masteryLevel', width: 200,
      render: (v) => {
        const val = parseFloat(v);
        const color = val >= 80 ? '#52c41a' : val >= 60 ? '#1677ff' : val >= 40 ? '#faad14' : '#f5222d';
        return <Progress percent={val} strokeColor={color} size="small" />;
      },
    },
  ];

  const weakColumns = [
    { title: '知识点', dataIndex: 'knowledgePointName' },
    { title: '科目', dataIndex: 'subject', width: 80, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '正确率', dataIndex: 'masteryLevel', width: 100, render: (v) => <Tag color="red">{parseFloat(v).toFixed(1)}%</Tag> },
    { title: '做题次数', dataIndex: 'totalCount', width: 100 },
  ];

  const recommendColumns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '科目', dataIndex: 'subject', width: 80, render: (v) => <Tag color="blue">{v}</Tag> },
    { title: '题型', dataIndex: 'type', width: 80 },
    { title: '难度', dataIndex: 'difficulty', width: 80, render: (v) => '★'.repeat(v) },
    { title: '题目', dataIndex: 'content', ellipsis: true },
  ];

  return (
    <div>
      <Title level={4}>薄弱知识点分析</Title>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={8}>
          <Card>
            <Statistic title="已学知识点" value={mastery.length} prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic title="薄弱知识点" value={weakPoints.length} prefix={<WarningOutlined />} valueStyle={{ color: weakPoints.length > 0 ? '#cf1322' : '#3f8600' }} />
          </Card>
        </Col>
        <Col span={8}>
          <Card>
            <Statistic
              title="平均掌握度"
              value={mastery.length > 0 ? (mastery.reduce((sum, m) => sum + parseFloat(m.masteryLevel), 0) / mastery.length).toFixed(1) : 0}
              suffix="%"
            />
          </Card>
        </Col>
      </Row>

      <Card title={<><WarningOutlined style={{ color: '#cf1322', marginRight: 8 }} />薄弱知识点（掌握度 &lt; 60%）</>} style={{ marginBottom: 16 }}>
        {weakPoints.length === 0 ? (
          <p style={{ color: '#999' }}>暂无薄弱知识点，继续保持！</p>
        ) : (
          <Table dataSource={weakPoints} columns={weakColumns} rowKey="knowledgePointId" pagination={false} />
        )}
      </Card>

      <Card title="推荐练习" extra={<Button type="primary" onClick={loadRecommend}>获取推荐题目</Button>} style={{ marginBottom: 16 }}>
        {recommended.length > 0 ? (
          <Table dataSource={recommended} columns={recommendColumns} rowKey="id" pagination={false} />
        ) : (
          <p style={{ color: '#999' }}>点击"获取推荐题目"，系统将根据薄弱知识点推荐练习</p>
        )}
      </Card>

      <Card title="所有知识点掌握度">
        {mastery.length === 0 ? (
          <p style={{ color: '#999' }}>暂无数据，开始做题后将在这里展示各知识点掌握度</p>
        ) : (
          <Table dataSource={mastery} columns={masteryColumns} rowKey="knowledgePointId" pagination={false} />
        )}
      </Card>
    </div>
  );
}
