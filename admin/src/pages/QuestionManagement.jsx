import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Space, Popconfirm } from 'antd';
import { getQuestionList, createQuestion, updateQuestion, deleteQuestion } from '../api/admin';

const subjectOptions = ['政治', '英语', '数学', '专业课'].map((s) => ({ value: s, label: s }));
const typeOptions = ['单选', '多选', '填空', '简答', '证明'].map((t) => ({ value: t, label: t }));

export default function QuestionManagement() {
  const [questions, setQuestions] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [filterSubject, setFilterSubject] = useState(undefined);
  const [form] = Form.useForm();

  const fetchQuestions = async (p = page) => {
    setLoading(true);
    try {
      const params = { page: p, size: 10 };
      if (filterSubject) params.subject = filterSubject;
      const res = await getQuestionList(params);
      setQuestions(res.data.content);
      setTotal(res.data.totalElements);
    } catch (err) {
      message.error(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchQuestions(); }, [page, filterSubject]);

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
        await updateQuestion(editingId, values);
        message.success('题目已更新');
      } else {
        await createQuestion(values);
        message.success('题目已创建');
      }
      setModalOpen(false);
      fetchQuestions();
    } catch (err) {
      if (err.message) message.error(err.message);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteQuestion(id);
      message.success('题目已删除');
      fetchQuestions();
    } catch (err) {
      message.error(err.message);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '科目', dataIndex: 'subject', width: 80 },
    { title: '题型', dataIndex: 'type', width: 80 },
    { title: '难度', dataIndex: 'difficulty', width: 60 },
    { title: '内容', dataIndex: 'content', ellipsis: true },
    { title: '年份', dataIndex: 'year', width: 70 },
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
        <h2 style={{ margin: 0 }}>题库管理</h2>
        <Space>
          <Select
            allowClear
            placeholder="筛选科目"
            style={{ width: 120 }}
            options={subjectOptions}
            value={filterSubject}
            onChange={(v) => { setFilterSubject(v); setPage(0); }}
          />
          <Button type="primary" onClick={openCreate}>新增题目</Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={questions}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: 10,
          total,
          onChange: (p) => setPage(p - 1),
        }}
      />
      <Modal
        title={editingId ? '编辑题目' : '新增题目'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={700}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="subject" label="科目" rules={[{ required: true }]}>
            <Select options={subjectOptions} />
          </Form.Item>
          <Form.Item name="type" label="题型" rules={[{ required: true }]}>
            <Select options={typeOptions} />
          </Form.Item>
          <Form.Item name="difficulty" label="难度 (1-5)" rules={[{ required: true }]}>
            <InputNumber min={1} max={5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="content" label="题目内容" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="answer" label="参考答案" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="analysis" label="解析">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="year" label="年份">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="source" label="来源">
            <Input />
          </Form.Item>
          <Form.Item name="knowledgePointId" label="知识点ID">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
