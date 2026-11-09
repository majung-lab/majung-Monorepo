/** 문자 발송 업체를 바꿔 끼우는 자리. 업체가 정해지면 같은 모양의 함수를 하나 더 만든다 */
export interface SmsSender {
  readonly name: string;
  send(to: string, text: string): Promise<void>;
}

/** 010-1234-5678 → 010-****-5678 (로그·응답에 번호를 그대로 남기지 않는다) */
export function maskPhone(phone: string) {
  return `${phone.slice(0, 3)}-****-${phone.slice(-4)}`;
}

/** 휴대폰 번호만 받는다. 하이픈·공백·+82는 허용하고 숫자만 남긴다 */
export function normalizePhone(input: string): string | null {
  let digits = input.replace(/[\s\-().]/g, '');
  if (digits.startsWith('+82')) digits = '0' + digits.slice(3);
  return /^01[016789][0-9]{7,8}$/.test(digits) ? digits : null;
}

/**
 * 개발용: 실제로 보내지 않고 터미널에 찍는다.
 * 문자 본문의 링크에는 동승자 토큰이 있으므로 로컬 개발에서만 쓴다.
 */
export function consoleSms(log: (line: string) => void = console.log): SmsSender {
  return {
    name: 'console',
    async send(to, text) {
      log(`[문자 발송(개발용, 실제 발송 안 함)] 받는 사람 ${maskPhone(to)}\n${text}`);
    },
  };
}

export function smsFromEnv(env = process.env): SmsSender {
  const provider = env.SMS_PROVIDER ?? 'console';
  if (provider === 'console') return consoleSms();
  throw new Error(`SMS_PROVIDER=${provider}는 아직 지원하지 않아요. console을 쓰거나 발송 업체 연동을 추가해 주세요.`);
}
