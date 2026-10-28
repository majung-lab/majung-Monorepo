// 웹소켓으로 방문 상태를 받고, 끊기면 5초마다 GET으로 대체한다 (화면동작정의 W01).
export function subscribeVisit(visitId: string, onMessage: (data: unknown) => void) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  const ws = new WebSocket(`${proto}://${location.host}/ws/visits/${visitId}`);
  ws.onmessage = (e) => onMessage(JSON.parse(e.data));
  return () => ws.close();
}
