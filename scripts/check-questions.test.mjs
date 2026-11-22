import assert from "node:assert/strict";
import { test } from "node:test";
import { checkQuestionFile } from "./check-questions.mjs";

/** 규칙을 모두 지키는 파일: 핵심 질문 3개, 질문마다 판정 3개와 설명형 1개 */
function validFile() {
  const questions = [];
  for (const cq of ["q1", "q2", "q3"]) {
    for (const n of [1, 2, 3]) {
      questions.push(`  - key: ${cq}-ox-${n}
    concept: core
    coreQuestion: ${cq}
    type: ox
    body: 문장 ${n}
    answer: O
    explanation: |-
      **O. 맞아요.**

      - 이유`);
    }
    questions.push(`  - key: ${cq}-explain
    concept: core
    coreQuestion: ${cq}
    type: descriptive
    body: 설명해 보세요
    explanation: "**결론이에요.** 이유"
    checklist: [하나, 둘, 셋, 넷]`);
  }
  return `lesson: {key: sample, title: 예시}
coreQuestions:
  - {key: q1, text: 첫째 질문}
  - {key: q2, text: 둘째 질문}
  - {key: q3, text: 셋째 질문}
questions:
${questions.join("\n")}
`;
}

const errorsOf = (text) => checkQuestionFile(text).errors;

test("규칙을 모두 지킨 파일은 오류가 없다", () => {
  assert.deepEqual(checkQuestionFile(validFile()), { skipped: false, errors: [] });
});

test("핵심 질문이 없는 옛 구조 파일은 검사하지 않는다", () => {
  const old = "lesson: {key: old}\nquestions:\n  - {key: a, type: ox, body: 가·나, explanation: 설명}\n";
  assert.deepEqual(checkQuestionFile(old), { skipped: true, errors: [] });
});

test("가운뎃점이 있으면 줄 번호와 함께 알린다", () => {
  const errors = errorsOf(validFile().replace("text: 첫째 질문", "text: 첫째·질문"));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /^3번째 줄: 가운뎃점/);
});

test("핵심 질문마다 판정 2개와 설명형 1개가 필요하다", () => {
  const noDescriptive = validFile().replace(/  - key: q2-explain[\s\S]*?checklist: \[하나, 둘, 셋, 넷\]\n/, "");
  assert.ok(errorsOf(noDescriptive).some((e) => e.includes("'q2': 설명형 문제가 0개")));

  const oneJudgment = validFile()
    .replace(/  - key: q3-ox-2[\s\S]*?- 이유\n/, "")
    .replace(/  - key: q3-ox-3[\s\S]*?- 이유\n/, "");
  assert.ok(errorsOf(oneJudgment).some((e) => e.includes("'q3': 판정 문제가 1개")));
});

test("숨긴 문제는 세지 않는다", () => {
  const hidden = validFile().replace("  - key: q1-explain\n", "  - key: q1-explain\n    status: hidden\n");
  assert.ok(errorsOf(hidden).some((e) => e.includes("'q1': 설명형 문제가 0개")));
});

test("레슨 전체로 판정 8개와 설명형 3개가 필요하다", () => {
  const fewer = validFile().replace(/  - key: q1-ox-3[\s\S]*?- 이유\n/, "").replace(/  - key: q2-ox-3[\s\S]*?- 이유\n/, "");
  assert.ok(errorsOf(fewer).some((e) => e.includes("판정 문제가 모두 7개")));
});

test("핵심 질문은 3~5개다", () => {
  const two = validFile().replace("  - {key: q3, text: 셋째 질문}\n", "");
  assert.ok(errorsOf(two).some((e) => e.includes("핵심 질문이 2개다")));
});

test("해설은 굵은 결론으로 시작해야 한다", () => {
  const plain = validFile().replace('explanation: "**결론이에요.** 이유"', "explanation: 결론이에요. **이유**");
  const errors = errorsOf(plain);
  assert.ok(errors.some((e) => e.includes("'q1-explain': 해설은 굵게 쓴 결론")));
});

test("출처 주소는 없는 페이지만 오류로 본다", async () => {
  const { classify } = await import("./check-sources.mjs");
  assert.equal(classify(200), "ok");
  assert.equal(classify(404), "broken");
  assert.equal(classify(410), "broken");
  assert.equal(classify(403), "unknown");
  assert.equal(classify(503), "unknown");
});
