// W03 종료 · 만료된 링크
import check from '../../assets/icons/check-big.svg';

// ponytail: 방문 기록 API 전까지 Figma 값.
const SAMPLE = { facility: '스타필드 하남', place: '북문 로비', metAt: '14:52' };

export default function EndedPage() {
  return (
    <main className="screen ended">
      <p className="ended-brand">마중</p>
      <div className="ended-hero">
        <div className="ended-badge"><img src={check} alt="" /></div>
        <h1 className="ended-title">모두 만났어요</h1>
        <p className="sub">운전자가 탑승 완료를 확인했어요</p>
      </div>
      <dl className="summary">
        <dt>장소</dt><dd>{SAMPLE.facility}</dd>
        <dt>만난 곳</dt><dd>{SAMPLE.place}</dd>
        <dt>만난 시각</dt><dd>{SAMPLE.metAt}</dd>
      </dl>
      <p className="ended-foot">이 방문은 끝났어요. 링크는 24시간 뒤 사라져요.</p>
    </main>
  );
}
