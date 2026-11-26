---
title: "OAuth 2.0과 소셜 로그인"
unit: 네트워크와 웹
order: 508
stage: 기초
summary: OAuth 2.0이 비밀번호 없이 무엇을 넘기는지, 인가 코드 흐름의 순서, 토큰 대신 코드를 거치는 이유, 소셜 로그인이 OAuth와 무엇이 다른지. 네 가지 질문에 스스로 답할 수 있게, RFC 규칙대로 만든 인가 서버로 흐름을 한 단계씩 밟아 보며 배워요.
minutes: 20
page: oauth2-login
verifiedBy:
  - verification/src/test/java/lab/devlessons/oauth2login/OAuth2FlowTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc6749.html
  - https://www.rfc-editor.org/rfc/rfc6750.html
  - https://www.rfc-editor.org/rfc/rfc7636.html
  - https://openid.net/specs/openid-connect-core-1_0.html
  - https://docs.spring.io/spring-security/reference/servlet/oauth2/login/core.html
updated: 2026-11-25
---

선수 레슨: 쿠키, 세션, 토큰과 HTTPS와 TLS. 세션과 JWT로 로그인을 유지하는 법과 HTTPS가 서버를 확인한다는 것을 알면 돼요.
