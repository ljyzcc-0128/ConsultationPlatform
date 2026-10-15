import { Navigate, Route, Routes } from 'react-router-dom';
import { AdminLayout } from '../../layouts/AdminLayout';
import { SourcesPage } from './SourcesPage';
import { TasksPage } from './TasksPage';
import { ContentReviewPage } from './ContentReviewPage';
import { UsersPage } from './UsersPage';
import { PushPage } from './PushPage';
import { AuditLogsPage } from './AuditLogsPage';
import { DlqPage } from './DlqPage';

/** 管理后台二级应用（懒加载入口在 App.tsx），普通用户不加载此 chunk */
export default function AdminApp() {
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<Navigate to="sources" replace />} />
        <Route path="sources" element={<SourcesPage />} />
        <Route path="tasks" element={<TasksPage />} />
        <Route path="content-review" element={<ContentReviewPage />} />
        <Route path="users" element={<UsersPage />} />
        <Route path="push" element={<PushPage />} />
        <Route path="audit-logs" element={<AuditLogsPage />} />
        <Route path="dlq" element={<DlqPage />} />
      </Route>
    </Routes>
  );
}
