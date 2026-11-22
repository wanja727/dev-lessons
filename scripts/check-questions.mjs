// 문제 파일이 레슨 표준(docs/lesson-standard.md 4장)을 지키는지 본다.
// 형식(필수 항목, 보기 번호, 핵심 질문에 묶였는지)은 Retrievo의 검사기가 본다. 여기서는 그 위에 얹는 이 저장소의 규칙만 본다.
//
//   node check-questions.mjs <문제 파일 디렉터리>
//
// 핵심 질문 구조로 쓴 파일(coreQuestions가 있는 파일)만 검사한다. 옛 구조 파일은 새로 쓸 때 검사 대상이 된다.
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { parse } from "yaml";

const JUDGMENT_TYPES = new Set(["multiple_choice", "ox", "code_predict", "line_pick"]);
const ALL = "all";

/** 가운뎃점으로 쓰이는 글자들: · ㆍ ・ ‧ */
const MIDDLE_DOT = /[·ㆍ・‧]/;

export const RULES = {
  coreQuestionsMin: 3,
  coreQuestionsMax: 5,
  judgmentPerCoreQuestion: 2,
  descriptivePerCoreQuestion: 1,
  judgmentPerLesson: 8,
  descriptivePerLesson: 3,
};

/**
 * 문제 파일 하나를 검사한다.
 * @returns {{skipped: boolean, errors: string[]}} skipped는 옛 구조 파일이라 검사하지 않았다는 뜻이다
 */
export function checkQuestionFile(text) {
  const file = parse(text);
  if (!file || !Array.isArray(file.coreQuestions)) {
    return { skipped: true, errors: [] };
  }
  const errors = [];

  text.split(/\r?\n/).forEach((line, i) => {
    if (MIDDLE_DOT.test(line)) {
      errors.push(`${i + 1}번째 줄: 가운뎃점을 쓰지 않는다. 쉼표나 "와"로 바꾼다: ${line.trim().slice(0, 60)}`);
    }
  });

  const core = file.coreQuestions;
  if (core.length < RULES.coreQuestionsMin || core.length > RULES.coreQuestionsMax) {
    errors.push(`핵심 질문이 ${core.length}개다. ${RULES.coreQuestionsMin}~${RULES.coreQuestionsMax}개로 짠다`);
  }

  // 숨긴 문제는 학습자에게 보이지 않으므로 세지 않는다
  const questions = (file.questions ?? []).filter((q) => q.status !== "hidden");
  const isJudgment = (q) => JUDGMENT_TYPES.has(q.type);
  const isDescriptive = (q) => q.type === "descriptive";

  for (const c of core) {
    const mine = questions.filter((q) => q.coreQuestion === c.key);
    const judgment = mine.filter(isJudgment).length;
    const descriptive = mine.filter(isDescriptive).length;
    if (judgment < RULES.judgmentPerCoreQuestion) {
      errors.push(`핵심 질문 '${c.key}': 판정 문제가 ${judgment}개다. ${RULES.judgmentPerCoreQuestion}개 이상 필요하다`);
    }
    if (descriptive < RULES.descriptivePerCoreQuestion) {
      errors.push(`핵심 질문 '${c.key}': 설명형 문제가 ${descriptive}개다. ${RULES.descriptivePerCoreQuestion}개 이상 필요하다`);
    }
  }

  const judgmentTotal = questions.filter(isJudgment).length;
  const descriptiveTotal = questions.filter(isDescriptive).length;
  if (judgmentTotal < RULES.judgmentPerLesson) {
    errors.push(`판정 문제가 모두 ${judgmentTotal}개다. 레슨마다 ${RULES.judgmentPerLesson}개 이상 필요하다`);
  }
  if (descriptiveTotal < RULES.descriptivePerLesson) {
    errors.push(`설명형 문제가 모두 ${descriptiveTotal}개다. 레슨마다 ${RULES.descriptivePerLesson}개 이상 필요하다`);
  }

  for (const q of questions) {
    const firstLine = String(q.explanation ?? "").trim().split("\n")[0];
    if (!/^\*\*[^*]+\*\*/.test(firstLine)) {
      errors.push(`문제 '${q.key}': 해설은 굵게 쓴 결론(**결론.**)으로 시작한다. 지금: ${firstLine.slice(0, 40)}`);
    }
  }

  return { skipped: false, errors };
}

function main(dir) {
  const names = readdirSync(dir).filter((n) => n.endsWith(".yaml") || n.endsWith(".yml")).sort();
  let checked = 0;
  let skipped = 0;
  let errorCount = 0;
  for (const name of names) {
    const result = checkQuestionFile(readFileSync(join(dir, name), "utf8"));
    if (result.skipped) {
      skipped++;
      continue;
    }
    checked++;
    for (const e of result.errors) {
      console.log(`오류 ${name}: ${e}`);
    }
    errorCount += result.errors.length;
  }
  console.log(`검사한 파일 ${checked}개, 옛 구조라 건너뛴 파일 ${skipped}개, 오류 ${errorCount}개`);
  if (checked === 0) {
    console.log("오류: 검사할 파일이 하나도 없다");
    process.exit(1);
  }
  process.exit(errorCount > 0 ? 1 : 0);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (!process.argv[2]) {
    console.error("사용법: node check-questions.mjs <문제 파일 디렉터리>");
    process.exit(2);
  }
  main(process.argv[2]);
}
