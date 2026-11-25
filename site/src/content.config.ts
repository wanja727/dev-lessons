import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

// 레슨 메타데이터. verifiedBy가 비어 있으면 빌드가 실패한다 (ADR-008: 레슨의 주장은 테스트로 검증한다)
const lessons = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/lessons' }),
  schema: z.object({
    title: z.string(),
    unit: z.enum(['자바 언어', 'Spring', 'JPA', 'DB', '운영체제', '동시성·분산', '네트워크와 웹', '객체지향 설계와 패턴', '자료구조와 알고리즘', '시스템 설계와 아키텍처', '운영체제와 동시성', '테스트와 운영', '스프링']),
    // 배우는 순서(선수 레슨을 지키는 순서)와 첫 화면에서 묶는 단계
    order: z.number().int().positive(),
    stage: z.enum(['기초', '동시성', '트랜잭션과 JPA', 'DB와 운영', '실무 시스템']),
    summary: z.string(),
    minutes: z.number().int().positive(),
    page: z.string(),
    verifiedBy: z.array(z.string()).min(1),
    sources: z.array(z.string().url()).min(1),
    updated: z.coerce.date(),
  }),
});

// 레슨 본문 자료 (docs/lesson-data-format.md). 이 파일의 칸을 채우면 페이지가 나온다.
// 본문 블록과 위젯의 칸은 느슨하게 받는다. 틀린 칸 때문에 문제가 생기면 그때 조인다
//
// 파일은 두 단계를 거친다 (docs/quality-plan.md 방법 4번).
//   stage: design  설계서. 질문, 결론, 본문이 말할 것, 위젯 계획, 문제 개요, 확인할 주장만 채운다. 페이지는 만들지 않는다
//   stage: full    본문까지 다 채운 파일. stage를 적지 않으면 full로 본다
// 설계 때 채운 칸은 full이 되어도 파일에 남는다. 페이지에는 나오지 않는다.
const text = z.string().min(1);
const block = z.record(z.any());
const conclusion = z.object({ lead: text, sub: text.optional(), subList: z.array(text).optional() });
const questionId = z.string().regex(/^q[1-9]$/);
const prerequisites = z.array(z.object({ slug: text, title: text })).default([]);
// 질문에 쓸 위젯의 계획. custom이면 왜 공통 위젯으로 안 되는지(why)를 적어야 한다
const widgetPlan = z.object({
  kind: z.enum(['classify', 'toggle', 'steps', 'compare', 'timeline', 'custom']),
  note: text,
  why: text.optional(),
}).refine((w) => w.kind !== 'custom' || !!w.why, { message: 'kind: custom이면 why에 공통 위젯으로 안 되는 이유를 적는다' });
// 테스트로 확인할 주장. how는 확인하는 방법, test는 이미 있는 테스트의 이름
const claims = z.array(z.object({ claim: text, how: text, test: text.optional() }));
// 도입부. 실무에서 이 주제를 만나는 장면, 스프링 개발자에게 무슨 의미인지, 이 레슨에서 확인할 것 (표준 1장 "도입부")
const intro = z.array(text).min(1).max(4);
// 이 주제에서 알아야 할 것과, 그것을 어디서 다루는지. 빠진 내용이 없는지 설계서에서 본다 (표준 1장 "알아야 할 것")
const covers = z.array(z.object({ topic: text, where: text }));

const designPage = z.object({
  stage: z.literal('design'),
  title: text,
  unit: text,
  roadmap: text, // 로드맵의 번호 (예: J6)
  type: z.enum(['동작 확인', '자바 실행', '설계', '개념 설명']),
  prerequisites,
  assume: text,
  example: text, // 레슨 전체에서 쓰는 예제 한 줄
  intro,
  covers: covers.min(3),
  codes: z.record(z.string()).default({}),
  links: z.record(z.string().url()).default({}),
  predict: z.object({ text: text, answer: text }),
  questions: z.array(z.object({
    id: questionId,
    title: text,
    conclusion,
    says: z.array(text).min(1),
    widget: widgetPlan.optional(), // 위젯을 두는 질문에만 적는다
    asks: z.array(text).min(2), // 문제 개요. 판정 문제 2개를 채우지 못하는 질문은 뺀다
  })).min(3).max(6),
  // "실무에서는" 구역이 보여 줄 것: 스프링의 코드나 설정, 흔한 실수와 고친 코드
  practice: z.object({ says: z.array(text).min(1) }),
  claims: claims.min(1),
  sources: z.array(text).default([]), // 테스트로 확인할 수 없는 것의 근거 (정의, 용어)
});

const fullPage = z.object({
  stage: z.literal('full'),
  title: text,
  description: text,
  unit: text,
  minutes: z.number().int().positive(),
  prerequisites,
  assume: text,
  // 도입부와 실무 구역은 2026-11-25부터 쓴다. 그 전에 쓴 레슨에는 없어서 선택으로 받는다
  intro: intro.optional(),
  covers: covers.optional(),
  codes: z.record(z.string()).default({}),
  links: z.record(z.string().url()).default({}),
  predict: z.object({
    title: text,
    text: text,
    code: text.optional(),
    label: text,
    options: z.array(z.object({ value: text, label: text })).min(2),
    answer: text,
    storageKey: text,
    wrong: text,
  }),
  questions: z.array(z.object({
    id: questionId,
    title: text,
    conclusion,
    says: z.array(text).optional(),
    widget: widgetPlan.optional(),
    asks: z.array(text).optional(),
    body: z.array(block).min(1),
    recall: text,
  })).min(3).max(6),
  practice: z.object({
    title: text.optional(),
    says: z.array(text).optional(),
    body: z.array(block).min(1),
  }).optional(),
  interview: z.object({
    question: text,
    model: z.array(z.object({ label: text, text: text })).min(3),
  }),
  // 검증 구역: 무엇으로 확인했는지 한 줄과 테스트 파일 링크 (docs/lesson-standard.md 7장)
  verified: text,
  footer: text,
  // 설계 때 채운 칸. 남겨 둔다
  roadmap: text.optional(),
  type: text.optional(),
  example: text.optional(),
  claims: claims.optional(),
  sources: z.array(text).optional(),
});

const lessonPages = defineCollection({
  loader: glob({ pattern: "**/*.yaml", base: "./src/content/lesson-pages" }),
  schema: z.preprocess(
    (data) => (data && typeof data === 'object' && !('stage' in data) ? { ...data, stage: 'full' } : data),
    z.discriminatedUnion('stage', [designPage, fullPage]),
  ),
});

export const collections = { lessons, lessonPages };

