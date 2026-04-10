import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import MainLayout from './layouts/MainLayout';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import QuestionPage from './pages/Question';
import ChatPage from './pages/Chat';
import DocumentPage from './pages/Document';
import ExamPage from './pages/Exam';
import AnalysisPage from './pages/Analysis';
import AiConfigPage from './pages/AiConfig';

function PrivateRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/login" />;
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<PrivateRoute><MainLayout /></PrivateRoute>}>
            <Route index element={<Navigate to="/dashboard" />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="question" element={<QuestionPage />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="document" element={<DocumentPage />} />
            <Route path="exam" element={<ExamPage />} />
            <Route path="analysis" element={<AnalysisPage />} />
            <Route path="ai-config" element={<AiConfigPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}
