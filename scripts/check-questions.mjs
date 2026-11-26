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
  coreQuestionsMax: 6,
  judgmentPerCoreQuestionMin: 2,
  judgmentPerCoreQuestionMax: 3,
  judgmentPerLessonMin: 6,
  judgmentPerLessonMax: 12,
  descriptivePerLessonMin: 1,
  descriptivePerLessonMax: 2,
  /** 보기 문제가 이 수 이상인 파일에서, 정답이 가장 긴 보기인 문제가 이 비율을 넘으면 경고한다 */
  longestAnswerMinQuestions: 4,
  longestAnswerMaxShare: 0.5,
  /** OX 문제가 이 수 이상일 때, 한쪽 답이 이 비율을 넘으면 경고한다 (모든 파일을 합쳐서 본다) */
  oxMinQuestions: 10,
  oxMaxShare: 0.7,
};

/**
 * 문제 파일 하나를 검사한다.
 * 오류는 CI를 실패시킨다. 경고는 알리기만 한다: 개수의 위쪽 한계를 넘은 것, 면접 질문(all)이 없는 것,
 * 정답이 가장 긴 보기에 몰린 것은 경고다.
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

  // 정답만 길게 쓰면 내용을 몰라도 가장 긴 보기를 골라 맞힌다. 앱이 보기를 섞어도 이 치우침은 남는다
  const withChoices = questions.filter((q) => Array.isArray(q.choices) && q.choices.length >= 2);
  const longest = withChoices.filter(isAnswerLongest);
  if (withChoices.length >= RULES.longestAnswerMinQuestions && longest.length / withChoices.length > RULES.longestAnswerMaxShare) {
    warnings.push(`보기 문제 ${withChoices.length}개 가운데 ${longest.length}개의 정답이 가장 긴 보기다. 오답도 정답만큼 자세히 쓰거나 정답을 줄인다: ${longest.map((q) => q.key).join(", ")}`);
  }

  return { skipped: false, errors, warnings };
}

/** 정답이 다른 모든 보기보다 긴가 */
function isAnswerLongest(q) {
  const lengths = q.choices.map((c) => String(c).trim().length);
  const answer = lengths[Number.parseInt(q.answer, 10) - 1];
  return answer !== undefined && lengths.filter((n) => n >= answer).length === 1;
}

/**
 * OX 문제의 답이 한쪽으로 몰렸는지 모든 파일을 합쳐서 본다. 파일 하나에는 OX가 한두 개뿐이라 파일마다는 볼 수 없다.
 * 틀린 문장을 내는 문제만 쓰면 내용을 몰라도 X를 골라 맞힌다.
 * @param {string[]} texts 문제 파일들의 내용
 * @returns {string[]} 경고
 */
export function checkOxBalance(texts) {
  const count = { O: 0, X: 0 };
  for (const text of texts) {
    const file = parse(text);
    if (!file || !Array.isArray(file.coreQuestions)) continue;
    for (const q of file.questions ?? []) {
      const answer = String(q.answer ?? "").trim().toUpperCase();
      if (q.type === "ox" && q.status !== "hidden" && answer in count) count[answer]++;
    }
  }
  const total = count.O + count.X;
  const [more, n] = count.O >= count.X ? ["O", count.O] : ["X", count.X];
  if (total < RULES.oxMinQuestions || n / total <= RULES.oxMaxShare) return [];
  return [`OX 문제 ${total}개 가운데 ${n}개의 답이 ${more}다. 새로 쓰는 OX 문제는 답이 ${more === "O" ? "X" : "O"}인 문장으로 낸다`];
}

function main(dir) {
  const names = readdirSync(dir).filter((n) => n.endsWith(".yaml") || n.endsWith(".yml")).sort();
  let checked = 0;
  let skipped = 0;
  let errorCount = 0;
  let warningCount = 0;
  const texts = names.map((name) => readFileSync(join(dir, name), "utf8"));
  names.forEach((name, i) => {
    const result = checkQuestionFile(texts[i]);
    if (result.skipped) {
      skipped++;
      return;
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
  });
  for (const w of checkOxBalance(texts)) {
    console.log(`경고 모든 파일: ${w}`);
    warningCount++;
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
