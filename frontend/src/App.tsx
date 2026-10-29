import { HashRouter, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import LandingPage from './pages/LandingPage';
import RegisterPage from './pages/RegisterPage';
import JoinPage from './pages/JoinPage';
import RestorePage from './pages/RestorePage';
import MePage from './pages/MePage';
import GroupPage from './pages/GroupPage';
import ConnectPage from './pages/ConnectPage';
import PrivacyPage from './pages/PrivacyPage';
import AdminPage from './pages/AdminPage';

// Every route from docs/API_CONTRACT.md §6 is wired here once, so nobody else needs to edit this file.
export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<LandingPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/join/:code" element={<JoinPage />} />
          <Route path="/back" element={<RestorePage />} />
          <Route path="/me" element={<MePage />} />
          <Route path="/group/:groupId" element={<GroupPage />} />
          <Route path="/group/:groupId/connect" element={<ConnectPage />} />
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route path="/admin" element={<AdminPage />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}
