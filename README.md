# Dev Lessons

백엔드 개발 개념을 **직접 조작하며 배우고, 테스트로 확인하는** 대화형 레슨 모음입니다.

- 사이트: https://wanja727.github.io/dev-lessons/
- 첫 레슨: [JPA N+1 문제](https://wanja727.github.io/dev-lessons/lessons/jpa-n-plus-1/)

## 왜 테스트로 검증하나요

학습 콘텐츠의 가장 흔한 문제는 그럴듯하게 틀린 설명입니다. 이 저장소는 레슨에 적은 동작을
`verification/`의 테스트로 증명하고, 코드가 바뀔 때마다 GitHub Actions에서 다시 확인합니다.

| 레슨의 주장 | 검증 테스트 |
| --- | --- |
| LAZY 컬렉션 접근 시 쿼리 1 + N번, EAGER로도 해결되지 않음 | `NPlusOneTest` |
| fetch join, @EntityGraph는 1번, 배치 페치(크기 5)는 1 + ⌈N/5⌉번 | `NPlusOneTest` |
| 컬렉션 fetch join + 페이징은 SQL 페이징 없이 메모리에서 자름 (HHH90003004) | `NPlusOneTest` |
| 내부 호출에는 @Transactional이 적용되지 않음, 두 가지 해결 방법 | `SelfInvocationTest` |

테스트는 Testcontainers로 실제 PostgreSQL을 띄우고, Hibernate `StatementInspector`로 DB에 나간 SQL을 직접 셉니다.

## 구조

```
site/           Astro 정적 사이트 (GitHub Pages)
  src/content/lessons/   레슨 메타데이터. verifiedBy(검증 테스트)가 없으면 빌드 실패
  src/pages/lessons/     대화형 레슨 페이지
verification/   Spring Boot 3.5 + JPA 예제와 검증 테스트
content/questions/   레슨별 문제 파일. 학습 앱 Retrievo가 가져간다
scripts/        문제 파일이 레슨 표준을 지키는지, 출처 주소가 열리는지 보는 검사
```

## 로컬 실행

```bash
# 사이트
cd site && npm ci && npm run dev

# 검증 테스트 (JDK 21, Gradle 8.14+, Docker 필요)
cd verification && gradle test
```

## 레슨 구성

모든 레슨은 같은 흐름을 따릅니다: 먼저 예측 → 배경 지식 → 직접 조작 → 함정 → 해결 방법 → 확인 문제 → 면접 답변 비교.
틀려도 괜찮은 예측과 꺼내 보는 연습을 넣은 것은, 읽기만 한 지식은 쉽게 잊히기 때문입니다.
