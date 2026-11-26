---
title: "웹 보안 기초"
unit: 네트워크와 웹
order: 510
stage: 기초
summary: XSS, CSRF, SQL 인젝션이 어떻게 일어나고 어떻게 막는지, 로그인 쿠키와 토큰을 어떻게 지키는지. 네 가지 질문에 스스로 답할 수 있게, 스프링 서버와 PostgreSQL에 실제로 공격 입력을 보내 보며 배워요.
minutes: 22
page: web-security-basics
verifiedBy:
  - verification/src/test/java/lab/devlessons/websecurity/WebSecurityTest.java
sources:
  - https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html
  - https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html
  - https://cheatsheetseries.owasp.org/cheatsheets/SQL_Injection_Prevention_Cheat_Sheet.html
  - https://www.rfc-editor.org/rfc/rfc6265.html
  - https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie
  - https://www.rfc-editor.org/rfc/rfc6797.html
  - https://docs.spring.io/spring-security/reference/servlet/exploits/index.html
updated: 2026-11-25
---

선수 레슨: 쿠키, 세션, 토큰과 CORS. 쿠키가 요청마다 자동으로 가고, 다른 출처의 폼 POST가 서버에서 처리된다는 것을 알면 돼요.
