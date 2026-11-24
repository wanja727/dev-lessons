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
  judgmentPerCoreQuestionMin: 2,
  judgmentPerCoreQuestionMax: 3,
  judgmentPerLessonMin: 6,
  judgmentPerLessonMax: 10,
  descriptivePerLessonMin: 1,
  descriptivePerLessonMax: 2,
};

/**
 * 문제 파일 하나를 검사한다.
 * 오류는 CI를 실패시킨다. 경고는 알리기만 한다: 개수의 위쪽 한계를 넘은 것과, 면접 질문(all)이 없는 것은 경고다.
 * 한계를 낮추기 전에 쓴 레슨이 있어서다 (docs/lesson-standard.md "아직 맞추지 않은 것"). 그 레슨들을 줄이면 오류로 바꾼다.
 * @returns {{skipped: boolean, errors: string[], warnings: string[]}} skipped는 옛 구조 파일이라 검사하지 않았다는 뜻이다
 */
export function checkQuestionFile(text) {
  const file = parse(text);
  if (!file || !Array.isArray(file.coreQuestions)) {
    return { skipped: true, errors: [], warnings: [] };
  }
  const errors = [];
  const warnings = [];

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
    const judgment = questions.filter((q) => q.coreQuestion === c.key && isJudgment(q)).length;
    if (judgment < RULES.judgmentPerCoreQuestionMin) {
      errors.push(`핵심 질문 '${c.key}': 판정 문제가 ${judgment}개다. ${RULES.judgmentPerCoreQuestionMin}개 이상 필요하다 (본문의 예시로 하나, 다른 상황으로 옮긴 것 하나)`);
    } else if (judgment > RULES.judgmentPerCoreQuestionMax) {
      warnings.push(`핵심 질문 '${c.key}': 판정 문제가 ${judgment}개다. ${RULES.judgmentPerCoreQuestionMax}개까지만 둔다`);
    }
  }

  const judgmentTotal = questions.filter(isJudgment).length;
  if (judgmentTotal < RULES.judgmentPerLessonMin) {
    errors.push(`판정 문제가 모두 ${judgmentTotal}개다. 레슨마다 ${RULES.judgmentPerLessonMin}개 이상 필요하다`);
  } else if (judgmentTotal > RULES.judgmentPerLessonMax) {
    warnings.push(`판정 문제가 모두 ${judgmentTotal}개다. 레슨마다 ${RULES.judgmentPerLessonMax}개까지만 둔다`);
  }

  const descriptive = questions.filter(isDescriptive);
  if (descriptive.length < RULES.descriptivePerLessonMin) {
    errors.push(`설명형 문제가 없다. 레슨마다 ${RULES.descriptivePerLessonMin}개 이상 필요하다`);
  } else if (!descriptive.some((q) => q.coreQuestion === ALL)) {
    warnings.push(`핵심 질문 전체를 잇는 설명형 문제(coreQuestion: ${ALL})가 없다. 면접 질문 하나를 둔다`);
  }
  if (descriptive.length > RULES.descriptivePerLessonMax) {
    warnings.push(`설명형 문제가 ${descriptive.length}개다. 레슨마다 ${RULES.descriptivePerLessonMax}개까지만 둔다. 질문마다의 설명형은 만들지 않는다`);
  }

  for (const q of questions) {
    const firstLine = String(q.explanation ?? "").trim().split("\n")[0];
    if (!/^\*\*[^*]+\*\*/.test(firstLine)) {
      errors.push(`문제 '${q.key}': 해설은 굵게 쓴 결론(**결론.**)으로 시작한다. 지금: ${firstLine.slice(0, 40)}`);
    }
  }

  return { skipped: false, errors, warnings };
}

function main(dir) {
  const names = readdirSync(dir).filter((n) => n.endsWith(".yaml") || n.endsWith(".yml")).sort();
  let checked = 0;
  let skipped = 0;
  let errorCount = 0;
  let warningCount = 0;
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
    for (const w of result.warnings) {
      console.log(`경고 ${name}: ${w}`);
    }
    errorCount += result.errors.length;
    warningCount += result.warnings.length;
  }
  console.log(`검사한 파일 ${checked}개, 옛 구조라 건너뛴 파일 ${skipped}개, 오류 ${errorCount}개, 경고 ${warningCount}개`);
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
