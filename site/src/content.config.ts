import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

// 레슨 메타데이터. verifiedBy가 비어 있으면 빌드가 실패한다 (ADR-008: 레슨의 주장은 테스트로 검증한다)
const lessons = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/lessons' }),
  schema: z.object({
    title: z.string(),
    unit: z.enum(['Java', 'Spring', 'JPA', 'DB', '운영체제', '동시성·분산', '네트워크·웹']),
    // 배우는 순서(선수 레슨을 지키는 순서)와 첫 화면에서 묶는 단계
    order: z.number().int().positive(),
    stage: z.enum(['기초', '동시성', '트랜잭션과 JPA', 'DB와 운영']),
    summary: z.string(),
    minutes: z.number().int().positive(),
    page: z.string(),
    verifiedBy: z.array(z.string()).min(1),
    sources: z.array(z.string().url()).min(1),
    updated: z.coerce.date(),
  }),
});

export const collections = { lessons };
