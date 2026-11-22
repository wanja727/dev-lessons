/**
 * 레슨 자료 파일의 글을 HTML로 바꾼다 (docs/lesson-data-format.md "글 쓰는 법").
 * 쓸 수 있는 표시는 세 가지뿐이다: **굵게**, `코드`, [글](주소). HTML은 해석하지 않고 글자 그대로 보여 준다.
 * 주소 자리에 link:이름 을 쓰면 자료 파일의 links에서 찾는다. lesson:슬러그 는 그 레슨 페이지로 간다.
 */
const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');

const escape = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export interface InlineOptions {
  links?: Record<string, string>;
}

function href(target: string, options: InlineOptions): string {
  if (target.startsWith('lesson:')) return `${BASE}/lessons/${target.slice(7)}/`;
  if (target.startsWith('link:')) {
    const url = options.links?.[target.slice(5)];
    if (!url) throw new Error(`자료 파일의 links에 '${target.slice(5)}'가 없다`);
    return url;
  }
  return target;
}

/** 예측 결과가 들어갈 자리. 페이지 스크립트가 채운다 */
const PREDICT_SLOT = '<span id="predict-result" class="muted"></span>';

export function inline(text: string, options: InlineOptions = {}): string {
  // 코드 조각을 먼저 떼어 둔다. 그 안의 *, [ 같은 글자는 표시로 읽지 않는다
  const codes: string[] = [];
  let out = String(text).replace(/`([^`]+)`/g, (_, code) => {
    codes.push(`<code>${escape(code)}</code>`);
    return `\u0000${codes.length - 1}\u0000`;
  });
  out = escape(out);
  out = out.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  out = out.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_, label, target) => `<a href="${href(target, options)}">${label}</a>`);
  out = out.replace(/\{\{predict-result\}\}/g, PREDICT_SLOT);
  return out.replace(/\u0000(\d+)\u0000/g, (_, i) => codes[Number(i)]);
}

/** 질문 수를 글로: "네 질문", "다섯 질문" */
export function countWord(n: number): string {
  return ['', '한', '두', '세', '네', '다섯'][n] ?? String(n);
}
