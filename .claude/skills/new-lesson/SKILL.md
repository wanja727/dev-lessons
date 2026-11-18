---
name: new-lesson
description: Dev Lessons 저장소에 새 대화형 레슨을 만들거나 기존 레슨을 고칠 때 쓴다. 레슨 페이지, 검증 테스트, 메타데이터, 문제 초안을 레슨 표준(docs/lesson-standard.md)대로 한 번에 만든다. "다음 레슨 만들어줘", "영속성 컨텍스트 레슨", "레슨 고쳐줘" 같은 요청에 사용.
---

# 새 레슨 만들기

먼저 `docs/lesson-standard.md`를 읽는다. 이 스킬은 그 표준을 실행하는 순서다.

## 1. 주제 정하기

- 사용자가 주제를 말하지 않았으면 `docs/roadmap.md`와 GitHub 이슈(`gh issue list --label lesson`)에서 선수 레슨이 모두 끝난 것 중 우선순위가 가장 높은 것을 제안한다
- 레슨 슬러그는 영문 소문자와 하이픈 (예: `jpa-persistence-context`)

## 2. 검증할 주장부터 적기

레슨을 쓰기 전에 "이 레슨이 말할 동작"을 목록으로 만든다. 예: "같은 id로 두 번 find하면 SELECT는 1번".
각 주장을 `verification/src/test/java/lab/devlessons/<주제>/` 테스트로 만든다.

- 공통 도구: `lab.devlessons.support.SqlCapture`(나간 SQL 수집), `PostgresContainerConfig`(Testcontainers PostgreSQL)
- JPA 동작은 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PostgresContainerConfig.class)`
- 데이터를 넣은 뒤 `em.flush(); em.clear(); SqlCapture.clear();` 로 시작 상태를 맞춘다
- 트랜잭션 경계가 중요한 주제는 `@SpringBootTest`(테스트에 트랜잭션이 없어야 한다)
- 엔티티는 주제 패키지에 따로 둔다. 다른 레슨의 엔티티를 고치지 않는다
- 로컬에 JDK 21이 없으면 푸시 후 CI(`Verify lessons`) 결과로 확인한다. **테스트가 실패하면 레슨 내용을 고친다** (테스트를 레슨에 맞추지 않는다)

## 3. 레슨 페이지

- 파일: `site/src/pages/lessons/<slug>.astro`, 레이아웃 `Base`, 공통 스타일 `site/src/styles/lesson.css`
- 흐름은 표준 1장 순서. 조작 위젯은 페이지 안 `<script is:inline>`으로 작성
- **중괄호 주의:** Astro 마크업에서 `{ }`는 표현식이다. 코드 예시는 프론트매터에 문자열로 두고 `<pre set:text={code} />`로 넣는다
- 링크는 `/dev-lessons/...`처럼 base 경로를 포함한다
- 마지막에 "검증" 섹션: 주장 목록 + 테스트 파일 GitHub 링크

## 4. 메타데이터

`site/src/content/lessons/<slug>.md` 프론트매터: title, unit, order(배우는 순서 번호, 선수 레슨보다 뒤), stage(기초·동시성·트랜잭션과 JPA·DB와 운영·실무 시스템 중 하나), summary, minutes, page, verifiedBy(테스트 경로, 1개 이상 필수), sources(공식 문서 URL), updated.
출처 URL은 `curl -o /dev/null -w '%{http_code}'`로 200인지 확인한다.

## 5. 문제 초안

표준 4장 규칙으로 연습 3개 이상, 판정 4개 이상을 만들어 `content/questions/<slug>.yaml`에 `status: draft`로 넣는다. 형식은 https://github.com/wanja727/retrievo/blob/main/docs/content-format.md. 같은 PR의 CI(`Check questions`)가 형식을 검사한다. 노션 문제 DB에는 더 넣지 않는다 (2026-11-17부터 문제 원본은 이 파일).

## 6. 마무리

- 브랜치를 만들고 커밋, PR 생성. PR 본문은 `.github/pull_request_template.md`를 채운다
- CI 두 개(`Verify lessons`, `Deploy site`)가 통과해야 완료
- 배포된 페이지를 375px 너비에서 열어 가로 스크롤과 콘솔 오류를 확인한다
- 로드맵 이슈를 닫고 `docs/roadmap.md` 상태를 갱신한다
