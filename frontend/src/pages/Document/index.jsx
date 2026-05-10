import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Card, Upload, Button, Table, Tag, message, Popconfirm, Typography, Select, Space, Switch } from 'antd';
import { UploadOutlined, DeleteOutlined, FileTextOutlined } from '@ant-design/icons';
import { uploadDocument, getDocuments, deleteDocument, setDocumentEnabled } from '../../api/document';
import { getKnowledgeBases } from '../../api/knowledgeBase';

const { Title } = Typography;

export default function DocumentPage() {
  const [searchParams] = useSearchParams();
  const [documents, setDocuments] = useState([]);
  const [knowledgeBases, setKnowledgeBases] = useState([]);
  const [selectedKbId, setSelectedKbId] = useState(Number(searchParams.get('knowledgeBaseId')) || null);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    getKnowledgeBases().then((res) => {
      const list = res.data || [];
      setKnowledgeBases(list);
      if (!selectedKbId && list.length > 0) {
        setSelectedKbId(list[0].id);
      }
    }).catch(() => {});
  }, []);

  const loadDocuments = async () => {
    if (!selectedKbId) return;
    try {
      const res = await getDocuments(selectedKbId);
      setDocuments(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadDocuments(); }, [selectedKbId]);

  const handleUpload = async (file) => {
    if (!selectedKbId) {
      message.warning('请先选择知识库');
      return false;
    }
    setUploading(true);
    try {
      await uploadDocument(file, selectedKbId);
      message.success('上传成功，正在处理中');
      loadDocuments();
    } catch (err) {
      message.error('上传失败');
    } finally {
      setUploading(false);
    }
    return false;
  };

  const handleDelete = async (id) => {
    try {
      await deleteDocument(id);
      message.success('删除成功');
      loadDocuments();
    } catch (err) {
      message.error('删除失败');
    }
  };

  const handleToggleEnabled = async (id, checked) => {
    try {
      await setDocumentEnabled(id, checked);
      message.success(checked ? '已启用' : '已禁用');
      loadDocuments();
    } catch (err) {
      message.error('操作失败');
    }
  };

  const statusMap = {
    PROCESSING: { color: 'processing', text: '处理中' },
    COMPLETED: { color: 'success', text: '已完成' },
    FAILED: { color: 'error', text: '失败' },
  };

  const columns = [
    {
      title: '文件名', dataIndex: 'originalFilename',
      render: (v) => <><FileTextOutlined style={{ marginRight: 8 }} />{v}</>,
    },
    {
      title: '大小', dataIndex: 'fileSize', width: 100,
      render: (v) => v ? `${(v / 1024).toFixed(1)} KB` : '-',
    },
    {
      title: '状态', dataIndex: 'status', width: 140,
      render: (v, record) => (
        <Space direction="vertical" size={0}>
          <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || v}</Tag>
          {v === 'FAILED' && record.errorMessage && (
            <span style={{ fontSize: 12, color: '#ff4d4f' }}>{record.errorMessage}</span>
          )}
        </Space>
      ),
    },
    {
      title: '启用', dataIndex: 'enabled', width: 60,
      render: (v, record) => (
        <Switch checked={v} onChange={(checked) => handleToggleEnabled(record.id, checked)} />
      ),
    },
    { title: '上传时间', dataIndex: 'uploadTime', width: 180 },
    {
      title: '操作', width: 80,
      render: (_, record) => (
        <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.id)}>
          <Button type="link" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Title level={4}>资料管理</Title>
      <Card style={{ marginBottom: 16 }}>
        <Space>
          <span>知识库：</span>
          <Select
            value={selectedKbId}
            onChange={setSelectedKbId}
            style={{ width: 200 }}
            options={knowledgeBases.map((kb) => ({ label: kb.name, value: kb.id }))}
          />
          <Upload beforeUpload={handleUpload} showUploadList={false} accept=".pdf,.txt,.md,.docx,.epub,.jpg,.jpeg,.png">
            <Button type="primary" icon={<UploadOutlined />} loading={uploading}>
              上传学习资料
            </Button>
          </Upload>
        </Space>
        <span style={{ marginLeft: 12, color: '#999' }}>支持 PDF、TXT、MD、DOCX、EPUB、JPG、PNG，上传后将自动向量化，可在 AI 问答中使用</span>
      </Card>
      <Table dataSource={documents} columns={columns} rowKey="id" pagination={false} />
    </div>
  );
}
