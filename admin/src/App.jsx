import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AdminLayout from './layouts/AdminLayout';
import Login from './pages/Login';
import DashboardPage from './pages/Dashboard';
import UserManagement from './pages/UserManagement';
import QuestionManagement from './pages/QuestionManagement';
import ExamManagement from './pages/ExamManagement';
import KnowledgePointManagement from './pages/KnowledgePointManagement';

function PrivateRoute({ children }) {
  const token = localStorage.getItem('admin_token');
  const role = localStorage.getItem('admin_role');
  if (!token || role !== 'ADMIN') {
    return <Navigate to="/login" />;
  }
  return children;
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<PrivateRoute><AdminLayout /></PrivateRoute>}>
            <Route index element={<Navigate to="/dashboard" />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="users" element={<UserManagement />} />
            <Route path="questions" element={<QuestionManagement />} />
            <Route path="exams" element={<ExamManagement />} />
            <Route path="knowledge-points" element={<KnowledgePointManagement />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}
