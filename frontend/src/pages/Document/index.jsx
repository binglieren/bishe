import { useEffect, useState } from 'react';
import { Card, Upload, Button, Table, Tag, message, Popconfirm, Typography } from 'antd';
import { UploadOutlined, DeleteOutlined, FileTextOutlined } from '@ant-design/icons';
import { uploadDocument, getDocuments, deleteDocument } from '../../api/document';

const { Title } = Typography;

export default function DocumentPage() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);

  const loadDocuments = async () => {
    try {
      const res = await getDocuments();
      setDocuments(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadDocuments(); }, []);

  const handleUpload = async (file) => {
    setUploading(true);
    try {
      await uploadDocument(file);
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

  const statusMap = {
    PROCESSING: { color: 'processing', text: '处理中' },
    COMPLETED: { color: 'success', text: '已完成' },
    FAILED: { color: 'error', text: '处理失败' },
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
      title: '状态', dataIndex: 'status', width: 100,
      render: (v) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text || v}</Tag>,
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
        <Upload beforeUpload={handleUpload} showUploadList={false} accept=".pdf,.txt,.md">
          <Button type="primary" icon={<UploadOutlined />} loading={uploading}>
            上传学习资料
          </Button>
        </Upload>
        <span style={{ marginLeft: 12, color: '#999' }}>支持 PDF、TXT 格式，上传后将自动向量化，可在 AI 问答中使用</span>
      </Card>
      <Table dataSource={documents} columns={columns} rowKey="id" pagination={false} />
    </div>
  );
}
