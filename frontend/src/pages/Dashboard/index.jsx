import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Button, message, Calendar, Tag, Typography, DatePicker } from 'antd';
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  BookOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { getUserInfo, checkIn, updateProfile } from '../../api/auth';
import { getSubjectAnalysis } from '../../api/analysis';
import dayjs from 'dayjs';

const { Title } = Typography;

export default function Dashboard() {
  const [userInfo, setUserInfo] = useState({});
  const [subjectStats, setSubjectStats] = useState({});

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

  const handleCheckIn = async () => {
    try {
      await checkIn(0);
      message.success('打卡成功！');
      loadData();
    } catch (err) {
      message.error(err.message);
    }
  };

  const daysUntilExam = userInfo.examDate
    ? dayjs(userInfo.examDate).diff(dayjs(), 'day')
    : null;

  return (
    <div>
      <Title level={4}>学习仪表盘</Title>

      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic title="累计打卡" value={userInfo.checkInDays || 0} suffix="天" prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="学习时长" value={userInfo.totalStudyMinutes || 0} suffix="分钟" prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="目标院校" value={userInfo.targetSchool || '未设置'} prefix={<BookOutlined />} valueStyle={{ fontSize: 16 }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="距离考试"
              value={daysUntilExam != null ? daysUntilExam : '--'}
              suffix={daysUntilExam != null ? '天' : ''}
              prefix={<TrophyOutlined />}
              valueStyle={daysUntilExam != null && daysUntilExam < 30 ? { color: '#cf1322' } : {}}
            />
          </Card>
        </Col>
      </Row>

      <Row gutter={16}>
        <Col span={12}>
          <Card title="今日打卡" extra={<Button type="primary" onClick={handleCheckIn}>打卡</Button>}>
            <p>目标专业：{userInfo.targetMajor || '未设置'}</p>
            <p>考试日期：{userInfo.examDate || '未设置'}</p>
            <p>开始备考：{userInfo.studyStartDate || '未设置'}</p>
          </Card>
        </Col>
        <Col span={12}>
          <Card title="各科掌握情况">
            {Object.keys(subjectStats).length === 0 ? (
              <p style={{ color: '#999' }}>暂无做题数据，开始做题后这里将显示各科掌握度</p>
            ) : (
              Object.entries(subjectStats).map(([subject, stats]) => (
                <div key={subject} style={{ marginBottom: 12 }}>
                  <Tag color="blue">{subject}</Tag>
                  <span>正确率: {stats.averageMastery}% | 做题 {stats.totalAttempts} 次 | 知识点 {stats.knowledgePointCount} 个</span>
                </div>
              ))
            )}
          </Card>
        </Col>
      </Row>
    </div>
  );
}
