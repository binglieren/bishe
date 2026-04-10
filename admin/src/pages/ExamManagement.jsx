import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Space, Popconfirm } from 'antd';
import { getExamList, createExam, updateExam, deleteExam } from '../api/admin';

const subjectOptions = ['政治', '英语', '数学', '专业课'].map((s) => ({ value: s, label: s }));

export default function ExamManagement() {
  const [exams, setExams] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form] = Form.useForm();

  const fetchExams = async (p = page) => {
    setLoading(true);
    try {
      const res = await getExamList(p, 10);
      setExams(res.data.content);
      setTotal(res.data.totalElements);
    } catch (err) {
      message.error(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchExams(); }, [page]);

  const openCreate = () => {
    setEditingId(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record) => {
    setEditingId(record.id);
    form.setFieldsValue(record);
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingId) {
        await updateExam(editingId, values);
        message.success('考试已更新');
      } else {
        await createExam(values);
        message.success('考试已创建');
      }
      setModalOpen(false);
      fetchExams();
    } catch (err) {
      if (err.message) message.error(err.message);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteExam(id);
      message.success('考试已删除');
      fetchExams();
    } catch (err) {
      message.error(err.message);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '科目', dataIndex: 'subject', width: 80 },
    { title: '时长(分钟)', dataIndex: 'durationMinutes', width: 100 },
    { title: '总分', dataIndex: 'totalScore', width: 70 },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 170,
      render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      width: 140,
      render: (_, record) => (
        <Space>
          <Button size="small" onClick={() => openEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>考试管理</h2>
        <Button type="primary" onClick={openCreate}>新增考试</Button>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={exams}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: 10,
          total,
          onChange: (p) => setPage(p - 1),
        }}
      />
      <Modal
        title={editingId ? '编辑考试' : '新增考试'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="subject" label="科目" rules={[{ required: true }]}>
            <Select options={subjectOptions} />
          </Form.Item>
          <Form.Item name="durationMinutes" label="考试时长（分钟）" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="totalScore" label="总分" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
