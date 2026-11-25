---
title: "쿠키, 세션, 토큰"
unit: 네트워크와 웹
order: 36
stage: 기초
summary: 쿠키가 왜 필요한지, 세션 방식과 토큰(JWT) 방식이 로그인 상태를 어떻게 유지하는지, 둘이 무엇을 얻고 무엇을 잃는지. 네 가지 질문에 스스로 답할 수 있게, 실제 서버가 돌려준 헤더와 응답으로 배워요.
minutes: 20
page: cookie-session-token
verifiedBy:
  - verification/src/test/java/lab/devlessons/cookiesessiontoken/CookieSessionTokenTest.java
  - verification/src/test/java/lab/devlessons/cookiesessiontoken/SpringSessionPracticeTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110.html#section-3.3
  - https://www.rfc-editor.org/rfc/rfc6265.html
  - https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0
  - https://www.rfc-editor.org/rfc/rfc7519.html
  - https://www.rfc-editor.org/rfc/rfc6750.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html
  - https://docs.spring.io/spring-session/reference/guides/boot-redis.html
  - https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
updated: 2026-11-25
---

선수 레슨: HTTP 요청과 응답(아직 없는 레슨). 요청과 응답에 헤더가 있다는 것만 알면 돼요.
