// W03 종료 · 만료된 링크
import { FACILITY, apById } from '../../data/dummy';
import { carAt, useVisit } from '../../data/store';
import check from '../../assets/icons/check-big.svg';

export default function EndedPage() {
  const visit = useVisit();
  const arrivedAt = carAt(visit, Date.now()).arrivedAt;
  return (
    <main className="screen ended">
      <p className="ended-brand">마중</p>
      <div className="ended-hero">
        <div className="ended-badge"><img src={check} alt="" /></div>
        <h1 className="ended-title">모두 만났어요</h1>
        <p className="sub">운전자가 탑승 완료를 확인했어요</p>
      </div>
      <dl className="summary">
        <dt>장소</dt><dd>{FACILITY.name}</dd>
        <dt>만난 곳</dt><dd>{apById(visit.meetId).name}</dd>
        <dt>만난 시각</dt><dd>{arrivedAt ? new Date(arrivedAt).toTimeString().slice(0, 5) : '-'}</dd>
      </dl>
      <p className="ended-foot">이 방문은 끝났어요. 링크는 24시간 뒤 사라져요.</p>
    </main>
  );
}
