// W02 운전자 제안 받기
import { useNavigate } from 'react-router-dom';
import MockMap from '../../features/map/MockMap';
import { proposalScene } from '../../features/map/scenes';
import arrowRight from '../../assets/icons/arrow-right.svg';

// ponytail: 제안 API 붙기 전 Figma 값. MeetingProposal + 장소 이름 응답으로 교체.
const SAMPLE = {
  from: { name: '서문 로비', short: '서문' },
  to: { name: '북문 로비', short: '북문' },
  reason: '15시부터 비 예보라, 지붕이 승하차 구역까지 이어진 북문으로 바꾸고 싶어요',
  timeoutMin: 10,
};

export default function ProposalPage() {
  const navigate = useNavigate();
  // TODO: 수락·거절 API 호출 후 W01로
  const answer = () => navigate('..', { relative: 'path' });

  return (
    <main className="screen">
      <MockMap scene={proposalScene}>
        <span className="chip chip-brand">마중</span>
      </MockMap>

      <section className="sheet sheet-dialog" role="dialog" aria-labelledby="proposal-title">
        <h1 id="proposal-title" className="dialog-title">운전자가 만날 곳을 바꾸자고 해요</h1>
        <div className="change">
          <span className="change-from">{SAMPLE.from.name}</span>
          <img src={arrowRight} alt="→" style={{ margin: "0 4px" }} />
          <span className="change-to">{SAMPLE.to.name}</span>
        </div>
        <p className="reason">{SAMPLE.reason}</p>
        <p className="note">{SAMPLE.timeoutMin}분 안에 답하지 않으면 {SAMPLE.from.name} 그대로예요</p>

        <div className="actions">
          <button className="btn-secondary" onClick={answer}>{SAMPLE.from.short} 그대로</button>
          <button className="btn-primary" onClick={answer}>{SAMPLE.to.short} 좋아요</button>
        </div>
      </section>
    </main>
  );
}
