import type { FastifyInstance } from 'fastify';
import type { CreateInviteRequest, InviteResult } from '@majung/shared';
import type { VisitRepository } from '../visit/visit.repository.ts';
import { HttpError } from '../../common/errors.ts';
import { maskPhone, normalizePhone, type SmsSender } from './sms.ts';

/** 방문 하나에서 보낼 수 있는 문자 수. 잘못 누르거나 남용해도 비용이 크게 나가지 않게 */
export const MAX_INVITES_PER_VISIT = 5;

export interface InviteOptions { visits: VisitRepository; sms: SmsSender; passengerWebUrl: string }

export async function inviteRoutes(app: FastifyInstance, { visits, sms, passengerWebUrl }: InviteOptions) {
  // 번호는 저장하지 않는다. 같은 requestId 재시도에 두 번 보내지 않도록 결과만 잠깐 기억한다 (서버 재시작 시 초기화)
  const sent = new Map<string, InviteResult>();
  const counts = new Map<string, number>();

  app.post<{ Params: { token: string }; Body: CreateInviteRequest }>('/visits/:token/invites', {
    schema: { body: { type: 'object', additionalProperties: false, required: ['requestId', 'phone'], properties: {
      requestId: { type: 'string', minLength: 1, maxLength: 128 },
      phone: { type: 'string', minLength: 1, maxLength: 32 },
    } } },
  }, async (req) => {
    const target = visits.inviteTarget(req.params.token);
    const key = `${target.visitId}:${req.body.requestId}`;
    const previous = sent.get(key);
    if (previous) return previous;
    const phone = normalizePhone(req.body.phone);
    if (!phone) throw new HttpError(400, 'INVALID_PHONE', '휴대폰 번호를 확인해 주세요');
    const count = counts.get(target.visitId) ?? 0;
    if (count >= MAX_INVITES_PER_VISIT) throw new HttpError(429, 'TOO_MANY_INVITES', `한 방문에서 문자는 ${MAX_INVITES_PER_VISIT}번까지 보낼 수 있어요`);
    const link = `${passengerWebUrl.replace(/\/+$/, '')}/v/${encodeURIComponent(target.passengerToken)}`;
    try {
      await sms.send(phone, `[마중] ${target.facilityName}에서 만나요. 링크를 누르면 만날 곳과 차 위치를 볼 수 있어요.\n${link}`);
    } catch (cause) {
      app.log.error({ provider: sms.name, name: (cause as Error)?.name }, 'SMS send failed');
      throw new HttpError(502, 'SMS_FAILED', '문자를 보내지 못했어요. 잠시 후 다시 시도해 주세요');
    }
    counts.set(target.visitId, count + 1);
    const result = { sentTo: maskPhone(phone) };
    sent.set(key, result);
    return result;
  });
}
