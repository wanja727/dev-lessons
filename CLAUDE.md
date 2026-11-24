# Dev Lessons

백엔드 개발 개념을 대화형 레슨으로 가르치고, 레슨의 주장을 테스트로 검증하는 저장소.

- 레슨을 만들거나 고칠 때는 `.claude/skills/new-lesson` 스킬과 `docs/lesson-standard.md`를 따른다
- 레슨 순서와 선수 관계: `docs/roadmap.md`
- `site/` Astro 사이트(GitHub Pages), `verification/` Spring Boot 3.5 + Testcontainers 검증 테스트
- 레슨에 적은 동작이 테스트와 다르면 테스트가 맞다. 레슨을 고친다
- 이 PC에서는 Astro 빌드와 JDK 21 테스트가 돌지 않을 수 있다. 그때는 CI 결과로 확인한다

## 올리는 방식

혼자 개발하는 저장소다. PR과 머지를 기다리는 시간을 없애려고 2026-11-24부터 main 하나로 관리한다.

- 끝난 변경은 main에 바로 커밋하고 push한다. PR과 이슈를 거치지 않는다
- push하면 CI가 검사하고, 통과해야 배포된다. push한 뒤 실행 결과를 확인하고, 실패하면 바로 고쳐 다시 올린다
- 커밋 메시지에 무엇을 왜 바꿨는지 적는다. PR 본문이 하던 일이다
- 올리기 전에 로컬에서 돌릴 수 있는 검사를 돌린다: `scripts`에서 `npm run check:questions`, `site`에서 `npx astro build`
- PR을 쓰는 경우: 되돌리기 어려운 변경이다. DB의 칸을 바꾸는 것, 틀이나 형식을 크게 고치는 것. 이때는 사용자에게 먼저 말하고, 머지는 사용자가 말했을 때 한다
- 이슈는 큰 일(과정 하나, 기능 하나)마다 하나만 만든다
- 여러 세션이 같은 폴더에서 동시에 일하면 서로의 작업이 섞인다. 그때는 세션마다 워크트리를 쓴다
- 레슨도 같다. 하나를 다 쓰면 main에 바로 올린다. 문제는 `status: draft`로 올리고, 검수한 뒤 `reviewed`로 바꿔 다시 올린다
