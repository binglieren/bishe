import { useEffect, useState } from 'react';
import { Table, Button, Tag, Modal, Input, message, Space, Popconfirm, Select } from 'antd';
import { getUserList, updateUserRole, resetUserPassword, deleteUser } from '../api/admin';

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [passwordModal, setPasswordModal] = useState({ open: false, userId: null });
  const [newPassword, setNewPassword] = useState('');

  const fetchUsers = async (p = page) => {
    setLoading(true);
    try {
      const res = await getUserList(p, 10);
      setUsers(res.data.content);
      setTotal(res.data.totalElements);
    } catch (err) {
      message.error(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchUsers(); }, [page]);

  const handleRoleChange = async (userId, role) => {
    try {
      await updateUserRole(userId, role);
      message.success('角色修改成功');
      fetchUsers();
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleResetPassword = async () => {
    if (!newPassword || newPassword.length < 6) {
      message.error('密码长度不能少于6位');
      return;
    }
    try {
      await resetUserPassword(passwordModal.userId, newPassword);
      message.success('密码重置成功');
      setPasswordModal({ open: false, userId: null });
      setNewPassword('');
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleDelete = async (userId) => {
    try {
      await deleteUser(userId);
      message.success('用户已删除');
      fetchUsers();
    } catch (err) {
      message.error(err.message);
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username' },
    { title: '邮箱', dataIndex: 'email', render: (v) => v || '-' },
    {
      title: '角色',
      dataIndex: 'role',
      render: (role, record) => (
        <Select
          value={role || 'USER'}
          size="small"
          style={{ width: 100 }}
          onChange={(val) => handleRoleChange(record.id, val)}
          options={[
            { value: 'USER', label: '普通用户' },
            { value: 'ADMIN', label: '管理员' },
          ]}
        />
      ),
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '-',
    },
    {
      title: '操作',
      render: (_, record) => (
        <Space>
          <Button
            size="small"
            onClick={() => setPasswordModal({ open: true, userId: record.id })}
          >
            重置密码
          </Button>
          <Popconfirm title="确定删除该用户？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>用户管理</h2>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{
          current: page + 1,
          pageSize: 10,
          total,
          onChange: (p) => setPage(p - 1),
        }}
      />
      <Modal
        title="重置密码"
        open={passwordModal.open}
        onOk={handleResetPassword}
        onCancel={() => { setPasswordModal({ open: false, userId: null }); setNewPassword(''); }}
      >
        <Input.Password
          placeholder="请输入新密码（至少6位）"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
      </Modal>
    </div>
  );
}
