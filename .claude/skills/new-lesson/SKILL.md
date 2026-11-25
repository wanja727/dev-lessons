---
name: new-lesson
description: Dev Lessons 저장소에 새 대화형 레슨을 만들거나 기존 레슨을 고칠 때 쓴다. 레슨 페이지, 검증 테스트, 메타데이터, 문제 초안을 레슨 표준(docs/lesson-standard.md)대로 한 번에 만든다. "다음 레슨 만들어줘", "영속성 컨텍스트 레슨", "레슨 고쳐줘" 같은 요청에 사용.
---

# 새 레슨 만들기

먼저 `docs/lesson-standard.md`를 읽는다. 이 스킬은 그 표준을 실행하는 순서다.

## 1. 주제 정하기

- 사용자가 주제를 말하지 않았으면 `docs/roadmap.md`와 GitHub 이슈(`gh issue list --label lesson`)에서 선수 레슨이 모두 끝난 것 중 우선순위가 가장 높은 것을 제안한다
- 레슨 슬러그는 영문 소문자와 하이픈 (예: `jpa-persistence-context`)

## 2. 알아야 할 것, 핵심 질문, 검증할 주장부터 적기

먼저 이 주제에서 학습자가 알아야 할 것을 전부 적는다(`covers`, 표준 1장). 분량을 생각하지 않고 적는다. 그 목록에서 핵심 질문(보통 3~5개)과 질문마다 결론 한 문장을 뽑고, 항목마다 어디서 다루는지 적는다. 질문에 들어가지 않는 것은 "실무에서는"이나 "더 알아보기"에 두고, 이 레슨이 맡지 않는 것은 맡는 레슨을 적는다. 도입부(`intro`)와 "실무에서는"에 보여 줄 스프링 코드(`practice.says`)도 함께 적는다. 사용자에게 보여 주고 시작한다. 그다음 "이 레슨이 말할 동작"을 목록으로 만든다. 예: "같은 id로 두 번 find하면 SELECT는 1번".
각 주장을 `verification/src/test/java/lab/devlessons/<주제>/` 테스트로 만든다.

- 공통 도구: `lab.devlessons.support.SqlCapture`(나간 SQL 수집), `PostgresContainerConfig`(Testcontainers PostgreSQL)
- JPA 동작은 `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `@Import(PostgresContainerConfig.class)`
- 데이터를 넣은 뒤 `em.flush(); em.clear(); SqlCapture.clear();` 로 시작 상태를 맞춘다
- 트랜잭션 경계가 중요한 주제는 `@SpringBootTest`(테스트에 트랜잭션이 없어야 한다)
- 엔티티는 주제 패키지에 따로 둔다. 다른 레슨의 엔티티를 고치지 않는다
- 웹 요청을 다루는 스프링 코드는 필요한 자동 설정만 켠 작은 앱을 띄워 확인한다. 본보기는 `cookiesessiontoken/SpringSessionPracticeTest.java`다 (DB와 배치 설정을 켜지 않는다)
- 로컬에서는 `verification`에서 `./gradlew test --tests '*테스트이름'`으로 그 레슨의 테스트만 돌린다. 테스트가 남긴 `build/ci-output/<슬러그>.json`을 `site/public/ci/`에 복사하면 개발 서버에서 실제 출력이 보인다. 로컬에 JDK 21이 없으면 푸시 후 CI(`Verify lessons`) 결과로 확인한다. **테스트가 실패하면 레슨 내용을 고친다** (테스트를 레슨에 맞추지 않는다)

## 3. 레슨 페이지

- `.astro` 페이지를 쓰지 않는다. 자료 파일 `site/src/content/lesson-pages/<slug>.yaml`의 칸을 채우면 틀이 페이지를 만든다. 칸은 `docs/lesson-data-format.md`에 있고, `cookie-session-token.yaml`을 본보기로 삼는다
- 본문을 쓰기 전에 같은 파일을 `stage: design`으로 만들어 설계 단계의 칸만 채우고 확인받는다 (`docs/lesson-data-format.md`의 "두 단계"). 승인되면 `stage` 줄을 지우고 나머지 칸을 채운다
- 구조는 표준 1장(핵심 질문)이고 틀이 정한다. 질문마다 결론, 본문 블록, 확인 문제, 다음 질문으로 잇는 말을 채운다
- 머리의 도입부(`intro`)를 쓴다: 실무에서 만나는 장면, 자바와 스프링 개발자에게 무슨 의미인지, 이 레슨에서 확인할 것
- "실무에서는"(`practice`)을 쓴다: 실제로 쓰는 스프링 코드와 설정, 흔한 실수 하나와 고친 코드. 테스트로 돌려 실제 출력을 붙이고, 돌리기 어려운 것은 공식 문서를 근거로 달고 그렇다고 밝힌다
- 다 쓴 뒤 `covers`의 항목이 적힌 곳에서 실제로 설명됐는지 하나씩 맞춰 본다. 분량을 맞추려고 내용을 빼지 않는다. 자료 파일이 60KB를 넘으면 겹치는 설명이 있는지, 나눌 주제인지 본다 (표준 1장 "분량")
- 위젯은 직접 눌러 봐야 이해되는 질문에만 둔다. 레슨에 1개 이상이면 된다. 설계 단계의 `widget` 칸도 위젯을 두는 질문에만 적는다
- 위젯은 공통 위젯(`classify`, `toggle`, `steps`, `compare`, `timeline`)에서 먼저 고른다. 안 되는 것만 `site/src/components/lesson-widgets/<slug>/<이름>.astro`에 직접 만들고, 왜 안 되는지 사용자에게 먼저 말한다
- 글에 쓸 수 있는 표시는 굵게, 코드 조각, 링크 세 가지다 (자료 형식 문서의 "글 쓰는 법"). 다른 레슨으로 가는 링크의 주소는 `lesson:슬러그`로 적는다
- 코드는 `codes`에 이름을 붙여 두고 블록에서 이름으로 쓴다. 한 줄 44자 이하, 강조는 `[[ ]]`(표준 3장)
- 화면이 그대로면 개발 서버를 다시 켠다. 오래 켜 둔 서버는 옛 스타일을 내놓기도 한다
- "검증" 구역(`verified`)은 무엇으로 확인했는지 한 줄과 테스트 파일 링크를 적은 글 하나다 (표준 7장)
- 실제 출력(`ci`)에는 `code`로 실행한 코드를 함께 넣어 한 상자로 보여 준다

## 4. 메타데이터

`site/src/content/lessons/<slug>.md` 프론트매터: title, unit, order(배우는 순서 번호, 선수 레슨보다 뒤), stage(기초, 동시성, 트랜잭션과 JPA, DB와 운영, 실무 시스템 중 하나), summary, minutes, page, verifiedBy(테스트 경로, 1개 이상 필수), sources(공식 문서 URL), updated.
출처 URL은 `curl -o /dev/null -w '%{http_code}'`로 200인지 확인한다.

## 5. 문제 초안

표준 4장 규칙으로 문제를 만들어 `content/questions/<slug>.yaml`에 `status: draft`로 넣는다. 핵심 질문마다 판정 2개다: 본문의 예시로 묻는 문제를 먼저, 다른 상황으로 옮긴 문제를 그다음에 적는다. "실무에서는"의 코드로 묻는 문제를 하나 이상 더해 관련된 질문의 셋째 문제로 적는다. 레슨 전체로 판정 6~12개, 설명형은 핵심 질문 전체를 잇는 면접 질문(`coreQuestion: all`) 하나를 포함해 1~2개다. 같은 것을 겉모습만 바꿔 다시 묻지 않는다. 파일의 `coreQuestions`에 핵심 질문을 적고 문제마다 `coreQuestion`으로 묶는다. 형식은 https://github.com/wanja727/retrievo/blob/main/docs/content-format.md. CI(`Check questions`)가 형식과 표준 4장의 규칙을 검사한다. 올리기 전에 `scripts`에서 `npm run check:questions`를 돌려 본다. 노션 문제 DB에는 더 넣지 않는다 (2026-11-17부터 문제 원본은 이 파일).

## 6. 마무리

- main에 바로 커밋하고 push한다 (`CLAUDE.md`의 "올리는 방식"). 커밋 메시지에 표준 5장의 숫자(자료 파일의 크기, 문제 수)를 적는다
- push한 뒤 CI(`Deploy site`, `Check questions`, 검증 코드를 바꿨으면 `Verify lessons`)가 통과하는지 확인한다. 실패하면 바로 고쳐 다시 올린다
- 배포된 페이지를 375px 너비에서 열어 가로 스크롤과 콘솔 오류를 확인한다. `?embed=1`을 붙여 앱 안 화면도 본다
- 로드맵 이슈를 닫고 `docs/roadmap.md` 상태를 갱신한다
