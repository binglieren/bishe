import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Space, Popconfirm } from 'antd';
import { getKnowledgePointList, createKnowledgePoint, updateKnowledgePoint, deleteKnowledgePoint } from '../api/admin';

const subjectOptions = ['政治', '英语', '数学', '专业课'].map((s) => ({ value: s, label: s }));

export default function KnowledgePointManagement() {
  const [points, setPoints] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [form] = Form.useForm();

  const fetchPoints = async (p = page) => {
    setLoading(true);
    try {
      const res = await getKnowledgePointList(p, 20);
      setPoints(res.data.content);
      setTotal(res.data.totalElements);
    } catch (err) {
      message.error(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchPoints(); }, [page]);

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
        await updateKnowledgePoint(editingId, values);
        message.success('知识点已更新');
      } else {
        await createKnowledgePoint(values);
        message.success('知识点已创建');
      }
      setModalOpen(false);
      fetchPoints();
    } catch (err) {
      if (err.message) message.error(err.message);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteKnowledgePoint(id);
      message.success('知识点已删除');
      fetchPoints();
    } catch (err) {
      message.error(err.message);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '科目', dataIndex: 'subject', width: 80 },
    { title: '名称', dataIndex: 'name', ellipsis: true },
    { title: '父节点ID', dataIndex: 'parentId', width: 100, render: (v) => v ?? '-' },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
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
        <h2 style={{ margin: 0 }}>知识点管理</h2>
        <Button type="primary" onClick={openCreate}>新增知识点</Button>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={points}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total,
          onChange: (p) => setPage(p - 1),
        }}
      />
      <Modal
        title={editingId ? '编辑知识点' : '新增知识点'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        width={500}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="subject" label="科目" rules={[{ required: true }]}>
            <Select options={subjectOptions} />
          </Form.Item>
          <Form.Item name="name" label="知识点名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="parentId" label="父节点ID">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
