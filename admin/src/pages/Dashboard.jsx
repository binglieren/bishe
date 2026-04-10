import { useEffect, useState } from 'react';
import { Row, Col, Card, Statistic, Spin, message } from 'antd';
import {
  UserOutlined,
  FileTextOutlined,
  FormOutlined,
  ApartmentOutlined,
  FileOutlined,
  MessageOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { getStatistics } from '../api/admin';

export default function DashboardPage() {
  const [stats, setStats] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getStatistics()
      .then((res) => setStats(res.data))
      .catch((err) => message.error(err.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const items = [
    { title: '用户总数', value: stats?.userCount, icon: <UserOutlined />, color: '#1890ff' },
    { title: '题目总数', value: stats?.questionCount, icon: <FileTextOutlined />, color: '#52c41a' },
    { title: '考试总数', value: stats?.examCount, icon: <FormOutlined />, color: '#722ed1' },
    { title: '知识点数', value: stats?.knowledgePointCount, icon: <ApartmentOutlined />, color: '#fa8c16' },
    { title: '文档总数', value: stats?.documentCount, icon: <FileOutlined />, color: '#13c2c2' },
    { title: '对话总数', value: stats?.chatSessionCount, icon: <MessageOutlined />, color: '#eb2f96' },
    { title: '考试记录', value: stats?.examRecordCount, icon: <BarChartOutlined />, color: '#f5222d' },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 24 }}>系统概览</h2>
      <Row gutter={[16, 16]}>
        {items.map((item) => (
          <Col xs={24} sm={12} md={8} lg={6} key={item.title}>
            <Card>
              <Statistic
                title={item.title}
                value={item.value ?? 0}
                prefix={<span style={{ color: item.color }}>{item.icon}</span>}
              />
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  );
}
