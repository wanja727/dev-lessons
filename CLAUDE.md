# Dev Lessons

백엔드 개발 개념을 대화형 레슨으로 가르치고, 레슨의 주장을 테스트로 검증하는 저장소.

- 레슨을 만들거나 고칠 때는 `.claude/skills/new-lesson` 스킬과 `docs/lesson-standard.md`를 따른다
- 레슨 순서와 선수 관계: `docs/roadmap.md`
- `site/` Astro 사이트(GitHub Pages), `verification/` Spring Boot 3.5 + Testcontainers 검증 테스트
- 레슨에 적은 동작이 테스트와 다르면 테스트가 맞다. 레슨을 고친다
- 이 PC에서는 Astro 빌드와 JDK 21 테스트가 돌지 않을 수 있다. 그때는 CI 결과로 확인한다
