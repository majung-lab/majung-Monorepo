import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import VisitPage from './pages/visit/VisitPage';
import ProposalPage from './pages/proposal/ProposalPage';
import EndedPage from './pages/ended/EndedPage';
import MovePage from './pages/move/MovePage';
import { VisitLoader } from './data/store';
import './styles.css';

// 링크 하나로 역할이 정해진다: /v/:token (운전자·동승자 토큰 분리)
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/v/:token" element={<VisitLoader />}>
          <Route index element={<VisitPage />} />
          <Route path="proposal" element={<ProposalPage />} />
          <Route path="move" element={<MovePage />} />
          <Route path="ended" element={<EndedPage />} />
        </Route>
        <Route path="*" element={<main className="screen ended"><h1>동승자 링크가 필요해요</h1><p>차량 앱의 QR을 스캔해 주세요.</p></main>} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
);
