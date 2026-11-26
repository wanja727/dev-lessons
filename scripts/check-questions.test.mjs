import assert from "node:assert/strict";
import { test } from "node:test";
import { checkOxBalance, checkQuestionFile } from "./check-questions.mjs";

const ox = (key, cq) => `  - key: ${key}
    concept: core
    coreQuestion: ${cq}
    type: ox
    body: 문장
    answer: O
    explanation: |-
      **O. 맞아요.**

      - 이유`;
const explain = (key, cq) => `  - key: ${key}
    concept: core
    coreQuestion: ${cq}
    type: descriptive
    body: 설명해 보세요
    explanation: "**결론이에요.** 이유"
    checklist: [하나, 둘, 셋, 넷]`;

/** 규칙을 모두 지키는 파일: 핵심 질문 3개, 질문마다 판정 2개, 면접 질문 하나 */
function validFile({ perQuestion = 2, extra = [] } = {}) {
  const questions = [];
  for (const cq of ["q1", "q2", "q3"]) {
    for (let n = 1; n <= perQuestion; n++) questions.push(ox(`${cq}-ox-${n}`, cq));
  }
  questions.push(explain("interview", "all"), ...extra);
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
const warningsOf = (text) => checkQuestionFile(text).warnings;

test("규칙을 모두 지킨 파일은 오류도 경고도 없다", () => {
  assert.deepEqual(checkQuestionFile(validFile()), { skipped: false, errors: [], warnings: [] });
});

test("핵심 질문이 없는 옛 구조 파일은 검사하지 않는다", () => {
  const old = "lesson: {key: old}\nquestions:\n  - {key: a, type: ox, body: 가·나, explanation: 설명}\n";
  assert.deepEqual(checkQuestionFile(old), { skipped: true, errors: [], warnings: [] });
});

test("가운뎃점이 있으면 줄 번호와 함께 알린다", () => {
  const errors = errorsOf(validFile().replace("text: 첫째 질문", "text: 첫째·질문"));
  assert.equal(errors.length, 1);
  assert.match(errors[0], /^3번째 줄: 가운뎃점/);
});

test("핵심 질문마다 판정 2개가 필요하다", () => {
  const one = validFile().replace(/  - key: q3-ox-2[\s\S]*?- 이유\n/, "");
  assert.ok(errorsOf(one).some((e) => e.includes("'q3': 판정 문제가 1개")));
});

test("핵심 질문마다 판정이 3개를 넘으면 경고한다", () => {
  assert.deepEqual(warningsOf(validFile({ perQuestion: 3 })), []);
  const four = validFile({ perQuestion: 3, extra: [ox("q1-ox-4", "q1")] });
  assert.deepEqual(errorsOf(four), []);
  assert.ok(warningsOf(four).some((w) => w.includes("'q1': 판정 문제가 4개")));
});

test("레슨 전체로 판정은 6개 이상이고, 12개를 넘으면 경고한다", () => {
  const thirteen = validFile({ perQuestion: 3, extra: [ox("q1-ox-4", "q1"), ox("q2-ox-4", "q2"), ox("q3-ox-4", "q3"), ox("q1-ox-5", "q1")] });
  assert.ok(warningsOf(thirteen).some((w) => w.includes("판정 문제가 모두 13개")));
});

test("숨긴 문제는 세지 않는다", () => {
  const hidden = validFile().replace("  - key: q1-ox-2\n", "  - key: q1-ox-2\n    status: hidden\n");
  assert.ok(errorsOf(hidden).some((e) => e.includes("'q1': 판정 문제가 1개")));
});

test("설명형은 하나 이상 있어야 하고, 2개를 넘으면 경고한다", () => {
  const none = validFile().replace(/  - key: interview[\s\S]*$/, "");
  assert.ok(errorsOf(none).some((e) => e.includes("설명형 문제가 없다")));

  const three = validFile({ extra: [explain("q1-explain", "q1"), explain("q2-explain", "q2")] });
  assert.deepEqual(errorsOf(three), []);
  assert.ok(warningsOf(three).some((w) => w.includes("설명형 문제가 3개")));
});

test("핵심 질문 전체를 잇는 면접 질문이 없으면 경고한다", () => {
  const noAll = validFile().replace("coreQuestion: all", "coreQuestion: q1");
  assert.ok(warningsOf(noAll).some((w) => w.includes("coreQuestion: all")));
});

test("핵심 질문은 3~6개다", () => {
  const two = validFile().replace("  - {key: q3, text: 셋째 질문}\n", "");
  assert.ok(errorsOf(two).some((e) => e.includes("핵심 질문이 2개다")));
});

test("해설은 굵은 결론으로 시작해야 한다", () => {
  const plain = validFile().replace('explanation: "**결론이에요.** 이유"', "explanation: 결론이에요. **이유**");
  assert.ok(errorsOf(plain).some((e) => e.includes("'interview': 해설은 굵게 쓴 결론")));
});

test("출처 주소는 없는 페이지만 오류로 본다", async () => {
  const { classify } = await import("./check-sources.mjs");
  assert.equal(classify(200), "ok");
  assert.equal(classify(404), "broken");
  assert.equal(classify(410), "broken");
  assert.equal(classify(403), "unknown");
  assert.equal(classify(503), "unknown");
});

const choice = (key, cq, choices, answer) => `  - key: ${key}
    concept: core
    coreQuestion: ${cq}
    type: multiple_choice
    body: 질문
    choices: [${choices.join(", ")}]
    answer: "${answer}"
    explanation: "**결론이에요.** 이유"`;
const LONG = ["남는다", "남지 않는다 같은 트랜잭션이라 함께 롤백된다", "오류가 난다"];
const EVEN = ["남는다 이미 커밋됐다", "남지 않는다 롤백된다", "오류가 나서 멈춘다"];

test("보기 문제의 절반 넘게 정답이 가장 긴 보기면 경고다", () => {
  const extra = ["a", "b", "c"].map((k) => choice(k, "q1", LONG, 2)).concat(choice("d", "q1", EVEN, 2));
  const warnings = warningsOf(validFile({ extra }));
  assert.equal(warnings.filter((w) => w.includes("가장 긴 보기")).length, 1);
  assert.match(warnings.join(" "), /보기 문제 4개 가운데 3개.*a, b, c/);
});

test("정답이 가장 긴 보기인 문제가 절반 이하면 경고하지 않는다", () => {
  const extra = [choice("a", "q1", LONG, 2), choice("b", "q1", LONG, 2), choice("c", "q1", EVEN, 2), choice("d", "q1", LONG, 1)];
  assert.equal(warningsOf(validFile({ extra })).filter((w) => w.includes("가장 긴 보기")).length, 0);
});

test("길이가 같은 보기가 있으면 가장 긴 보기로 세지 않는다", () => {
  const same = ["가나다라", "마바사아", "자차"];
  const extra = ["a", "b", "c", "d"].map((k) => choice(k, "q1", same, 1));
  assert.equal(warningsOf(validFile({ extra })).filter((w) => w.includes("가장 긴 보기")).length, 0);
});

test("OX의 답이 한쪽으로 몰리면 모든 파일을 합쳐 경고한다", () => {
  const allO = [validFile(), validFile()]; // O 12개
  assert.match(checkOxBalance(allO).join(""), /OX 문제 12개 가운데 12개의 답이 O다/);
  const mixed = validFile().replaceAll("answer: O", "answer: X");
  assert.deepEqual(checkOxBalance([validFile(), mixed]), []);
  assert.deepEqual(checkOxBalance([validFile().split("questions:")[0] + "questions: []" + String.fromCharCode(10)]), []);
});
