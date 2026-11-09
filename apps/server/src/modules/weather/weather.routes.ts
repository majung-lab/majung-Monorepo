import type { FastifyInstance } from 'fastify';
import type { FacilityWeather, VisitMode } from '@majung/shared';
import type { VisitRepository } from '../visit/visit.repository.ts';
import { HttpError } from '../../common/errors.ts';
import { rankAccessPoints, type WeatherLookup } from './weather.ts';

/** C04 지점 선택·동승자 화면에서 쓰는 시설 날씨와 추천 순서. 시설 정보처럼 공개 데이터라 토큰이 필요 없다 */
export async function weatherRoutes(app: FastifyInstance, { visits, weather }: { visits: VisitRepository; weather: WeatherLookup }) {
  app.get<{ Params: { id: string }; Querystring: { mode?: VisitMode } }>('/facilities/:id/weather', {
    schema: {
      params: { type: 'object', properties: { id: { type: 'string', pattern: '^[1-9][0-9]*$', maxLength: 16 } }, required: ['id'] },
      querystring: { type: 'object', additionalProperties: false, properties: { mode: { enum: ['PICKUP', 'PARK', 'DROPOFF'] } } },
    },
  }, async (req): Promise<FacilityWeather> => {
    const facility = visits.facilities().find((f) => f.id === req.params.id);
    if (!facility) throw new HttpError(404, 'FACILITY_NOT_FOUND', '시설을 찾을 수 없어요');
    const kind = req.query.mode === 'PARK' ? 'PARKING_ENTRANCE' : 'PICKUP';
    const current = await weather(facility.lat, facility.lng);
    return { weather: current, ranking: rankAccessPoints(facility.accessPoints.filter((p) => p.allowed && p.kind === kind), current) };
  });
}
