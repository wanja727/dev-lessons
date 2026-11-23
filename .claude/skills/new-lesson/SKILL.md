---
name: new-lesson
description: Dev Lessons 저장소에 새 대화형 레슨을 만들거나 기존 레슨을 고칠 때 쓴다. 레슨 페이지, 검증 테스트, 메타데이터, 문제 초안을 레슨 표준(docs/lesson-standard.md)대로 한 번에 만든다. "다음 레슨 만들어줘", "영속성 컨텍스트 레슨", "레슨 고쳐줘" 같은 요청에 사용.
---

# 새 레슨 만들기

먼저 `docs/lesson-standard.md`를 읽는다. 이 스킬은 그 표준을 실행하는 순서다.

## 1. 주제 정하기

- 사용자가 주제를 말하지 않았으면 `docs/roadmap.md`와 GitHub 이슈(`gh issue list --label lesson`)에서 선수 레슨이 모두 끝난 것 중 우선순위가 가장 높은 것을 제안한다
- 레슨 슬러그는 영문 소문자와 하이픈 (예: `jpa-persistence-context`)

## 2. 핵심 질문과 검증할 주장부터 적기

먼저 핵심 질문 3~5개와 질문마다 결론 한 문장을 적는다(표준 1장). 사용자에게 보여 주고 시작한다. 그다음 "이 레슨이 말할 동작"을 목록으로 만든다. 예: "같은 id로 두 번 find하면 SELECT는 1번".
각 주장을 `verification/src/test/java/lab/devlessons/<주제>/` 테스트로 만든다.

- 공통 도구: `lab.devlessons.support.SqlCapture`(나간 SQL 수집), `PostgresContainerConfig`(Testcontainers PostgreSQL)
- JPA 동작은 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PostgresContainerConfig.class)`
- 데이터를 넣은 뒤 `em.flush(); em.clear(); SqlCapture.clear();` 로 시작 상태를 맞춘다
- 트랜잭션 경계가 중요한 주제는 `@SpringBootTest`(테스트에 트랜잭션이 없어야 한다)
- 엔티티는 주제 패키지에 따로 둔다. 다른 레슨의 엔티티를 고치지 않는다
- 로컬에 JDK 21이 없으면 푸시 후 CI(`Verify lessons`) 결과로 확인한다. **테스트가 실패하면 레슨 내용을 고친다** (테스트를 레슨에 맞추지 않는다)

## 3. 레슨 페이지

- `.astro` 페이지를 쓰지 않는다. 자료 파일 `site/src/content/lesson-pages/<slug>.yaml`의 칸을 채우면 틀이 페이지를 만든다. 칸은 `docs/lesson-data-format.md`에 있고, `jpa-n-plus-1.yaml`과 `tx-basics.yaml`을 본보기로 삼는다
- 본문을 쓰기 전에 같은 파일을 `stage: design`으로 만들어 설계 단계의 칸만 채우고 확인받는다 (`docs/lesson-data-format.md`의 "두 단계"). 승인되면 `stage` 줄을 지우고 나머지 칸을 채운다
- 구조는 표준 1장(핵심 질문)이고 틀이 정한다. 질문마다 결론, 본문 블록, 위젯 하나, 확인 문제, 다음 질문으로 잇는 말을 채운다
- 위젯은 공통 위젯(`classify`, `toggle`, `steps`, `compare`, `timeline`)에서 먼저 고른다. 안 되는 것만 `site/src/components/lesson-widgets/<slug>/<이름>.astro`에 직접 만들고, 왜 안 되는지 사용자에게 먼저 말한다
- 글에 쓸 수 있는 표시는 굵게, 코드 조각, 링크 세 가지다 (자료 형식 문서의 "글 쓰는 법"). 다른 레슨으로 가는 링크의 주소는 `lesson:슬러그`로 적는다
- 코드는 `codes`에 이름을 붙여 두고 블록에서 이름으로 쓴다. 한 줄 44자 이하, 강조는 `[[ ]]`(표준 3장)
- 화면이 그대로면 개발 서버를 다시 켠다. 오래 켜 둔 서버는 옛 스타일을 내놓기도 한다
- 마지막에 접어 둔 "검증" 구역(`verified`): 상황과 확인한 결과 표, 테스트 하나, `run` 블록

## 4. 메타데이터

`site/src/content/lessons/<slug>.md` 프론트매터: title, unit, order(배우는 순서 번호, 선수 레슨보다 뒤), stage(기초·동시성·트랜잭션과 JPA·DB와 운영·실무 시스템 중 하나), summary, minutes, page, verifiedBy(테스트 경로, 1개 이상 필수), sources(공식 문서 URL), updated.
출처 URL은 `curl -o /dev/null -w '%{http_code}'`로 200인지 확인한다.

## 5. 문제 초안

표준 4장 규칙으로 핵심 질문마다 판정 2개 이상과 설명형 1개를 만들어 `content/questions/<slug>.yaml`에 `status: draft`로 넣는다. 파일의 `coreQuestions`에 핵심 질문을 적고 문제마다 `coreQuestion`으로 묶는다. 형식은 https://github.com/wanja727/retrievo/blob/main/docs/content-format.md. 같은 PR의 CI(`Check questions`)가 형식과 표준 4장의 규칙을 검사한다. 올리기 전에 `scripts`에서 `npm run check:questions`를 돌려 본다. 노션 문제 DB에는 더 넣지 않는다 (2026-11-17부터 문제 원본은 이 파일).

## 6. 마무리

- 브랜치를 만들고 커밋, PR 생성. PR 본문은 `.github/pull_request_template.md`를 채운다
- CI 두 개(`Verify lessons`, `Deploy site`)가 통과해야 완료
- 배포된 페이지를 375px 너비에서 열어 가로 스크롤과 콘솔 오류를 확인한다. `?embed=1`을 붙여 앱 안 화면도 본다
- 로드맵 이슈를 닫고 `docs/roadmap.md` 상태를 갱신한다
