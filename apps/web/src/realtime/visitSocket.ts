import type { VisitSnapshot } from '@majung/shared';

/** HTTP polling continues during reconnects, and also refreshes expired proposals. */
export function subscribeVisit(token: string, onMessage: (data: VisitSnapshot) => void) {
  let stopped = false;
  let socket: WebSocket | undefined;
  let retry: ReturnType<typeof setTimeout> | undefined;
  const connect = () => {
    if (stopped) return;
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    socket = new WebSocket(`${proto}://${location.host}/ws/visits/${encodeURIComponent(token)}`);
    socket.onmessage = (event) => {
      try { onMessage(JSON.parse(event.data)); } catch { /* HTTP refresh recovers malformed messages. */ }
    };
    socket.onerror = () => socket?.close();
    socket.onclose = () => { if (!stopped) retry = setTimeout(connect, 5000); };
  };
  retry = setTimeout(connect, 0);
  return () => { stopped = true; clearTimeout(retry); socket?.close(); };
}
