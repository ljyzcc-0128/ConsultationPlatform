import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  message,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { api } from '../../api/client';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';
import { useCrudTable } from '../../hooks/useCrudTable';
import type { AdminUser, Role, UserStatus } from '../../api/types';

interface UserFormValues {
  username: string;
  displayName: string;
  email: string | null;
  roles: Role[];
  status: UserStatus;
}

type UserUpdateBody = Partial<Omit<AdminUser, 'id' | 'createdAt'>> & { id: string };

export function UsersPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<AdminUser | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm<UserFormValues>();

  /** 指南 6.2：用户管理是典型 CRUD，使用通用 useCrudTable */
  const table = useCrudTable<AdminUser>({
    queryKey: ['admin-users'],
    fetcher: () => api.get<never, AdminUser[]>('/v1/admin/users'),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin-users'] });

  const createMutation = useMutation({
    mutationFn: (values: UserFormValues) => api.post('/v1/admin/users', values),
    onSuccess: () => {
      message.success('用户已创建');
      setModalOpen(false);
      form.resetFields();
      invalidate();
    },
    onError: () => message.error('创建失败：用户名可能已存在'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, ...body }: UserUpdateBody) =>
      api.put(`/v1/admin/users/${id}`, body),
    onSuccess: () => {
      message.success('已保存');
      setEditing(null);
      invalidate();
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.delete(`/v1/admin/users/${id}`),
    onSuccess: () => {
      message.success('用户已删除');
      invalidate();
    },
  });

  const openCreate = () => {
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (record: AdminUser) => {
    setEditing(record);
    form.setFieldsValue({
      username: record.username,
      displayName: record.displayName,
      email: record.email,
      roles: record.roles,
      status: record.status,
    });
  };

  const modalTitle = editing ? `编辑用户：${editing.username}` : '新增用户';
  const modalOpenMerged = modalOpen || Boolean(editing);

  const closeModal = () => {
    setModalOpen(false);
    setEditing(null);
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>用户与权限</h1>
          <div className="sub">
            一期区分「普通用户 / 管理员」两种角色；前端权限仅为体验优化，真正的权限校验由后端完成。
          </div>
        </div>
        <div className="head-actions">
          <Space>
            <Input.Search
              placeholder="按用户名/姓名筛选"
              allowClear
              style={{ width: 200 }}
              onSearch={(v) => table.setFilter('username', v)}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
              新增用户
            </Button>
          </Space>
        </div>
      </div>

      <div className="admin-table-wrap">
        {table.query.isError ? (
          <ErrorState error={table.query.error} onRetry={() => table.reload()} />
        ) : table.query.isLoading ? (
          <ListSkeleton rows={4} />
        ) : (
          <Table<AdminUser>
            rowKey="id"
            dataSource={table.data}
            pagination={table.pagination}
            columns={[
              { title: '用户名', dataIndex: 'username', width: 120 },
              { title: '姓名', dataIndex: 'displayName', width: 160 },
              { title: '邮箱', dataIndex: 'email', width: 220 },
              {
                title: '角色',
                dataIndex: 'roles',
                width: 140,
                render: (roles: string[]) => (
                  <Space size={4}>
                    {roles.map((r) => (
                      <Tag key={r} color={r === 'ADMIN' ? 'purple' : 'blue'}>
                        {r === 'ADMIN' ? '管理员' : '普通用户'}
                      </Tag>
                    ))}
                  </Space>
                ),
              },
              {
                title: '状态',
                dataIndex: 'status',
                width: 90,
                render: (v: UserStatus, record) => (
                  <Switch
                    checked={v === 'ACTIVE'}
                    checkedChildren="启用"
                    unCheckedChildren="停用"
                    onChange={(val) =>
                      updateMutation.mutate({ id: record.id, status: val ? 'ACTIVE' : 'DISABLED' })
                    }
                  />
                ),
              },
              { title: '创建时间', dataIndex: 'createdAt', width: 110 },
              {
                title: '操作',
                width: 140,
                render: (_, record) => (
                  <Space>
                    <Button size="small" onClick={() => openEdit(record)}>
                      编辑
                    </Button>
                    <Popconfirm
                      title="确定删除该用户？"
                      onConfirm={() => deleteMutation.mutate(record.id)}
                    >
                      <Button size="small" danger>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ]}
          />
        )}

        <Modal
          title={modalTitle}
          open={modalOpenMerged}
          onCancel={closeModal}
          onOk={() => form.submit()}
          confirmLoading={createMutation.isPending || updateMutation.isPending}
        >
          <Form
            form={form}
            layout="vertical"
            onFinish={(values) => {
              if (editing) {
                updateMutation.mutate({ id: editing.id, ...values });
              } else {
                createMutation.mutate(values);
              }
            }}
            initialValues={{ roles: ['USER'], status: 'ACTIVE' }}
          >
            <Form.Item
              name="username"
              label="用户名"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input disabled={Boolean(editing)} />
            </Form.Item>
            <Form.Item name="displayName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
              <Input />
            </Form.Item>
            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <Input />
            </Form.Item>
            <Form.Item name="roles" label="角色">
              <Select
                mode="multiple"
                options={[
                  { value: 'USER', label: '普通用户' },
                  { value: 'ADMIN', label: '管理员' },
                ]}
              />
            </Form.Item>
          </Form>
        </Modal>
      </div>
    </>
  );
}
