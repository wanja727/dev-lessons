/**
 * 레슨 코드 블록에 색을 입힌다. 자바, JPQL·SQL, 설정 파일이 한 블록에 섞여 있어서 가볍게만 나눈다.
 * - 주석(//, --, 줄 맨 앞의 #), 문자열, 어노테이션, 키워드에 색을 준다
 * - 코드에서 [[이렇게]] 감싼 부분은 강조 표시한다. 이 줄에서 봐야 할 곳을 가리킬 때 쓴다
 * 쓰는 법: <pre set:html={highlight(code)} />
 */
const KEYWORDS = [
  // 자바
  'class', 'interface', 'public', 'private', 'protected', 'static', 'final', 'void', 'new', 'return',
  'for', 'if', 'else', 'while', 'try', 'catch', 'throw', 'throws', 'extends', 'implements', 'null', 'true', 'false',
  // SQL
  'select', 'from', 'where', 'join', 'left', 'inner', 'on', 'in', 'order', 'by', 'limit', 'offset',
  'insert', 'into', 'values', 'update', 'set', 'delete', 'and', 'or', 'group', 'having', 'any',
];

const TOKEN = new RegExp(
  [
    '(\\[\\[.+?\\]\\])', // 1 강조
    '(\\/\\/.*$|--.*$|^\\s*#.*$)', // 2 주석
    '("""[\\s\\S]*?"""|"(?:[^"\\\\\\n]|\\\\.)*")', // 3 문자열 (여러 줄 텍스트 블록 포함)
    '(@[A-Za-z]\\w*)', // 4 어노테이션
    `\\b(${KEYWORDS.join('|')})\\b`, // 5 키워드
  ].join('|'),
  'gm',
);

const escape = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
/** 주석과 문자열 안에 있는 [[강조]]도 살린다 */
const marks = (s: string) => s.replace(/\[\[(.+?)\]\]/g, '<mark class="hl">$1</mark>');

export function highlight(code: string): string {
  return tokens(escape(code));
}

function tokens(text: string): string {
  return text.replace(TOKEN, (m, mark, comment, str, anno, key) => {
    // 강조한 부분 안에도 문법 색을 입힌다. 안 그러면 같은 단어가 강조 안팎에서 다른 색이 된다
    if (mark) return `<mark class="hl">${tokens(mark.slice(2, -2))}</mark>`;
    if (comment) return `<span class="c-com">${marks(comment)}</span>`;
    if (str) return `<span class="c-str">${marks(str)}</span>`;
    if (anno) return `<span class="c-key c-anno">${anno}</span>`;
    if (key) return `<span class="c-key">${key}</span>`;
    return m;
  });
}
