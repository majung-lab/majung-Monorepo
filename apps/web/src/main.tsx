import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import VisitPage from './pages/visit/VisitPage';
import ProposalPage from './pages/proposal/ProposalPage';
import EndedPage from './pages/ended/EndedPage';
import './styles.css';

// 링크 하나로 역할이 정해진다: /v/:token (운전자·동승자 토큰 분리)
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/v/:token" element={<VisitPage />} />
        <Route path="/v/:token/proposal" element={<ProposalPage />} />
        <Route path="/v/:token/ended" element={<EndedPage />} />
        <Route path="*" element={<EndedPage />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
