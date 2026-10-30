// W03 서버에서 완료 또는 취소된 방문
import { useVisit } from '../../data/store';
import check from '../../assets/icons/check-big.svg';

export default function EndedPage() {
  const { snapshot } = useVisit();
  const { visit, facility, rating } = snapshot;
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
    <p className="data-notice">예시 시설·승하차 지점 데이터</p>
    <p className="ended-foot">이 방문은 끝났어요.<br />링크 만료: {new Date(visit.expiresAt).toLocaleString('ko-KR')}</p>
  </main>;
}
