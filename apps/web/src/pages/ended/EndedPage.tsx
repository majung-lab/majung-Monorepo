// W03 서버에서 완료 또는 취소된 방문
import { useVisit } from '../../data/store';
import { api } from '../../api/client';
import check from '../../assets/icons/check-big.svg';

export default function EndedPage() {
  const { snapshot, token, busy, mutate } = useVisit();
  const { visit, facility, rating, coverageQuestion: ask } = snapshot;
  const completed = visit.phase === 'COMPLETED';
  if (!completed && visit.phase !== 'CANCELLED') return null;
  const meet = facility.accessPoints.find((p) => p.id === visit.confirmedAccessPointId);
  return <main className="screen ended">
    <p className="ended-brand">마중</p>
    <div className="ended-hero">
      {completed && <div className="ended-badge"><img src={check} alt="" /></div>}
      <h1 className="ended-title">{completed ? '방문이 완료됐어요' : '방문이 취소됐어요'}</h1>
      <p className="sub">{completed ? '운전자가 완료를 확인했어요' : '운전자가 방문을 종료했어요'}</p>
    </div>
    <dl className="summary">
      <dt>장소</dt><dd>{facility.name}</dd>
      <dt>만날 곳</dt><dd>{meet?.name || '-'}</dd>
      {visit.completedAt && <><dt>완료 시각</dt><dd>{new Date(visit.completedAt).toLocaleString('ko-KR')}</dd></>}
      {visit.parkingFloor && <><dt>주차 층</dt><dd>{visit.parkingFloor}</dd></>}
      {rating && <><dt>장소 평가</dt><dd>{rating.rating} / 5{rating.comment && <p className="sub">{rating.comment}</p>}</dd></>}
    </dl>
    {completed && ask && <section className="coverage-ask" aria-live="polite">
      {ask.answer === null ? <>
        <p className="coverage-title">오는 길에 비를 피할 수 있었나요?</p>
        <p className="sub">{ask.from} → {ask.to} · 답해 주시면 다음 추천에 반영돼요</p>
        <div className="actions">
          <button className="btn-secondary" disabled={busy} onClick={() => void mutate((s) => api.coverage(token, s.visit.version, false))}>지붕 없었어요</button>
          <button className="btn-primary" disabled={busy} onClick={() => void mutate((s) => api.coverage(token, s.visit.version, true))}>지붕 있었어요</button>
        </div>
      </> : <p className="coverage-title">고마워요! {ask.answer ? '지붕 있음' : '지붕 없음'}으로 알려 주셨어요</p>}
    </section>}
    <p className="data-notice">예시 시설·승하차 지점 데이터</p>
    <p className="ended-foot">이 방문은 끝났어요.<br />링크 만료: {new Date(visit.expiresAt).toLocaleString('ko-KR')}</p>
  </main>;
}
