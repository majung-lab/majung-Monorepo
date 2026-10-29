// W04 위치 바꾸기 (동승자) — 핀 조정 → 응답 대기
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import MockMap from '../../features/map/MockMap';
import { adjustScene } from '../../features/map/scenes';
import back from '../../assets/icons/back.svg';
import pinTarget from '../../assets/icons/pin-target.svg';
import locate from '../../assets/icons/locate.svg';

// ponytail: 핀 위치 → 가까운 출입구 계산은 시설 데이터 붙일 때. 지금은 Figma 예시값.
const SAMPLE = {
  current: '북문 로비',
  target: { name: '서문 로비', note: '서문 회전문 앞', distM: 120 },
};

export default function MovePage() {
  const navigate = useNavigate();
  const [moved, setMoved] = useState(false);
  const [waiting, setWaiting] = useState(false);
  const toVisit = () => navigate('..', { relative: 'path' });

  return (
    <main className="screen">
      <header className="topbar">
        <button className="icon-btn" onClick={toVisit} aria-label="뒤로"><img src={back} alt="" /></button>
        <h1>만날 곳 바꾸기</h1>
      </header>

      <MockMap scene={adjustScene} onPan={waiting ? undefined : () => setMoved(true)}>
        <p className="hint">지도를 움직여 핀을 맞춰 주세요</p>
        <div className="target" aria-hidden>
          <span className="target-tag">여기로 바꿀까요?</span>
          <img src={pinTarget} alt="" />
          <span className="target-shadow" />
        </div>
        <button className="locate" aria-label="내 위치로"><img src={locate} alt="" /></button>
      </MockMap>

      <section className="sheet">
        {waiting ? (
          <>
            <p className="waiting-title">운전자 응답 기다리는 중</p>
            <p className="sub">
              {SAMPLE.current} → {SAMPLE.target.name} · 수락 전까지는 {SAMPLE.current} 그대로예요
            </p>
            <button className="btn-cancel" onClick={toVisit}>제안 취소</button>
          </>
        ) : (
          <>
            <div className="target-info">
              <div>
                <p className="target-name">{SAMPLE.target.name}</p>
                <p className="target-note">{SAMPLE.target.note}</p>
              </div>
              <p className="sub">지금 위치에서 {SAMPLE.target.distM}m</p>
            </div>
            {/* TODO: 제안 API 호출 */}
            <button className="btn-primary" disabled={!moved} onClick={() => setWaiting(true)}>
              여기로 조정하기
            </button>
          </>
        )}
      </section>
    </main>
  );
}
