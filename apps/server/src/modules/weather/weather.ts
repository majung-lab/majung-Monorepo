import type { AccessPoint, RankedAccessPoint, Weather } from '@majung/shared';

/** 날씨를 못 받아도 방문은 만들 수 있어야 하므로 실패는 null로 돌려준다. */
export type WeatherLookup = (lat: number, lng: number) => Promise<Weather | null>;

/** 기상청 단기예보 항목 한 칸 (category: TMP 기온, POP 강수확률, PTY 강수형태, SKY 하늘상태) */
export type ForecastItem = { category: string; fcstDate: string; fcstTime: string; fcstValue: string };

const HOURS_AHEAD = 6;
const RAIN_CHANCE_ALERT = 60;
const CACHE_MS = 30 * 60 * 1000;
const KST_MS = 9 * 60 * 60 * 1000;

/** 위경도 → 기상청 격자(nx, ny). 기상청 동네예보 격자 변환(LCC) 공식 그대로 */
export function toGrid(lat: number, lng: number): { nx: number; ny: number } {
  const RE = 6371.00877, GRID = 5.0, SLAT1 = 30.0, SLAT2 = 60.0, OLON = 126.0, OLAT = 38.0, XO = 43, YO = 136;
  const rad = Math.PI / 180;
  const re = RE / GRID, slat1 = SLAT1 * rad, slat2 = SLAT2 * rad, olon = OLON * rad, olat = OLAT * rad;
  let sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
  sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
  let sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
  sf = (Math.pow(sf, sn) * Math.cos(slat1)) / sn;
  const ro = (re * sf) / Math.pow(Math.tan(Math.PI * 0.25 + olat * 0.5), sn);
  const ra = (re * sf) / Math.pow(Math.tan(Math.PI * 0.25 + lat * rad * 0.5), sn);
  let theta = lng * rad - olon;
  if (theta > Math.PI) theta -= 2 * Math.PI;
  if (theta < -Math.PI) theta += 2 * Math.PI;
  theta *= sn;
  return { nx: Math.floor(ra * Math.sin(theta) + XO + 0.5), ny: Math.floor(ro - ra * Math.cos(theta) + YO + 0.5) };
}

/** 단기예보는 하루 8번(02·05·…·23시) 발표되고 10분쯤 뒤에 받을 수 있다. 지금 받을 수 있는 가장 최근 발표 시각 */
export function baseTime(now: Date): { date: string; time: string } {
  const kst = new Date(now.getTime() + KST_MS - 10 * 60 * 1000);
  let hour = kst.getUTCHours();
  const slot = [23, 20, 17, 14, 11, 8, 5, 2].find((h) => h <= hour);
  if (slot === undefined) { kst.setUTCDate(kst.getUTCDate() - 1); hour = 23; } else hour = slot;
  return { date: kst.toISOString().slice(0, 10).replaceAll('-', ''), time: `${String(hour).padStart(2, '0')}00` };
}

/** 예보 항목들 → 화면에 띄울 날씨 한 줄. 앞으로 6시간 안에 비가 오면 rainSoon */
export function summarize(items: ForecastItem[], now: Date, source: Weather['source'] = 'KMA'): Weather | null {
  const kst = new Date(now.getTime() + KST_MS);
  const currentHour = `${kst.toISOString().slice(0, 10).replaceAll('-', '')}${String(kst.getUTCHours()).padStart(2, '0')}00`;
  const hours = new Map<string, Record<string, string>>();
  for (const item of items) {
    const key = item.fcstDate + item.fcstTime;
    if (key < currentHour) continue;
    hours.set(key, { ...hours.get(key), [item.category]: item.fcstValue });
  }
  const upcoming = [...hours.entries()].sort(([a], [b]) => a.localeCompare(b)).slice(0, HOURS_AHEAD);
  const first = upcoming[0];
  if (!first) return null;
  const [, now0] = first;
  const temp = Number(now0.TMP);
  const tempC = Number.isFinite(temp) ? Math.round(temp) : null;
  const pops = upcoming.map(([, v]) => Number(v.POP)).filter(Number.isFinite);
  const rainChance = pops.length ? Math.max(...pops) : null;
  const pty = (v: Record<string, string>) => Number(v.PTY ?? 0);
  const isSnow = (v: Record<string, string>) => pty(v) === 3;
  const deg = tempC === null ? '' : ` · ${tempC}°`;
  const pct = rainChance === null ? '' : ` · 강수확률 ${rainChance}%`;
  let condition: Weather['condition'] = Number(now0.SKY) >= 3 ? 'CLOUDY' : 'CLEAR';
  let summary: string;
  let rainSoon = false;
  if (pty(now0) > 0) {
    condition = isSnow(now0) ? 'SNOW' : 'RAIN';
    summary = `지금 ${condition === 'SNOW' ? '눈' : '비'}${deg}`;
    rainSoon = true;
  } else {
    const wet = upcoming.find(([, v]) => pty(v) > 0);
    if (wet) {
      summary = `${Number(wet[0].slice(8, 10))}시부터 ${isSnow(wet[1]) ? '눈' : '비'}${pct}`;
      rainSoon = true;
    } else if (rainChance !== null && rainChance >= RAIN_CHANCE_ALERT) {
      summary = `비 올 수 있어요${pct}`;
      rainSoon = true;
    } else {
      summary = `${condition === 'CLEAR' ? '맑음' : '흐림'}${deg}`;
    }
  }
  return { condition, tempC, rainChance, rainSoon, summary, checkedAt: now.toISOString(), source };
}

/** 공공데이터포털 기상청 단기예보 API. 키가 없으면 늘 null */
export function kmaWeather(key = process.env.KMA_SERVICE_KEY, clock: () => Date = () => new Date()): WeatherLookup {
  const cache = new Map<string, { at: number; value: Weather | null }>();
  return async (lat, lng) => {
    if (!key) return null;
    const now = clock();
    const { nx, ny } = toGrid(lat, lng);
    const base = baseTime(now);
    const cacheKey = `${nx},${ny},${base.date}${base.time}`;
    const hit = cache.get(cacheKey);
    if (hit && now.getTime() - hit.at < CACHE_MS) return hit.value;
    try {
      // 포털은 '인코딩 키'와 '디코딩 키'를 둘 다 준다. 어느 쪽을 넣어도 되게 한다
      const serviceKey = key.includes('%') ? key : encodeURIComponent(key);
      const url = `https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=${serviceKey}&dataType=JSON&numOfRows=400&pageNo=1&base_date=${base.date}&base_time=${base.time}&nx=${nx}&ny=${ny}`;
      const res = await fetch(url, { signal: AbortSignal.timeout(3000) });
      if (!res.ok) return null;
      const body = (await res.json()) as { response?: { header?: { resultCode?: string }; body?: { items?: { item?: ForecastItem[] } } } };
      if (body.response?.header?.resultCode !== '00') return null;
      const value = summarize(body.response.body?.items?.item ?? [], now);
      cache.set(cacheKey, { at: now.getTime(), value });
      return value;
    } catch {
      return null;
    }
  };
}

/** 키 없이 화면·추천을 시연할 때: WEATHER_DEMO=rain 이면 늘 '곧 비' */
export function demoWeather(kind: 'rain' | 'clear', clock: () => Date = () => new Date()): WeatherLookup {
  return async () => {
    const now = clock();
    const hour = (new Date(now.getTime() + KST_MS).getUTCHours() + 1) % 24;
    return kind === 'rain'
      ? { condition: 'CLOUDY', tempC: 14, rainChance: 70, rainSoon: true, summary: `${hour}시부터 비 · 강수확률 70%`, checkedAt: now.toISOString(), source: 'DEMO' }
      : { condition: 'CLEAR', tempC: 18, rainChance: 10, rainSoon: false, summary: '맑음 · 18°', checkedAt: now.toISOString(), source: 'DEMO' };
  };
}

export function weatherFromEnv(env = process.env): WeatherLookup {
  if (env.KMA_SERVICE_KEY) return kmaWeather(env.KMA_SERVICE_KEY);
  if (env.WEATHER_DEMO === 'rain' || env.WEATHER_DEMO === 'clear') return demoWeather(env.WEATHER_DEMO);
  return async () => null;
}

/** 이 운전자가 지점별로 남긴 평가 평균 (1~5). 차량 앱은 좋았어요 5 · 보통 3 · 별로 1 */
export type PastRatings = ReadonlyMap<string, number>;
const GOOD = 4, BAD = 2;

/**
 * 추천 순서.
 * 1) 비·눈이 오거나 6시간 안에 올 것 같으면 지붕 있는 동선 먼저
 * 2) 이 운전자가 전에 좋게 평가한 곳 먼저, 별로라고 한 곳은 뒤로
 * 3) 걷는 시간이 짧은 순
 * 날씨·평가를 모르면 그 단계는 건너뛴다. 첫 번째가 추천.
 */
export function rankAccessPoints(points: AccessPoint[], weather: Weather | null, ratings: PastRatings = new Map()): RankedAccessPoint[] {
  const wet = !!weather?.rainSoon;
  const walk = (p: AccessPoint) => p.walkMinutes ?? Number.MAX_SAFE_INTEGER;
  const liked = (p: AccessPoint) => { const r = ratings.get(p.id); return r === undefined ? 1 : r >= GOOD ? 0 : r <= BAD ? 2 : 1; };
  const sorted = [...points].sort((a, b) => (wet ? Number(b.covered) - Number(a.covered) : 0) || liked(a) - liked(b) || walk(a) - walk(b) || Number(a.id) - Number(b.id));
  const shortest = Math.min(...points.map(walk));
  const word = weather?.condition === 'SNOW' || weather?.summary.includes('눈') ? '눈' : '비';
  // 걷는 시간은 화면 태그로 따로 보이므로 이유는 짧게 한 줄
  return sorted.map((p, i) => {
    let reason: string | null = null;
    if (wet && p.covered) reason = i === 0 ? `${word} 예보라 지붕 아래에서 타고 내려요` : '지붕 있는 동선이에요';
    else if (wet) reason = `지붕이 없어 ${word === '눈' ? '눈을' : '비를'} 맞을 수 있어요`;
    else if (liked(p) === 0 && i === 0) reason = '지난번에 좋았던 곳이에요';
    else if (liked(p) === 2) reason = '지난번 평가가 좋지 않았어요';
    else if (i === 0 && p.walkMinutes !== null && p.walkMinutes === shortest) reason = '기다리는 곳에서 가장 가까워요';
    else if (i === 0 && sorted.some((q) => liked(q) === 2)) reason = '지난번 별로였던 곳 대신 추천해요';
    return { accessPointId: p.id, recommended: i === 0, reason };
  });
}
