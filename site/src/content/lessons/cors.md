---
title: "CORS"
unit: 네트워크와 웹
order: 63
stage: 기초
summary: 브라우저가 다른 출처의 응답을 막는 이유, CORS가 무엇을 허락하는지, 사전 요청이 가는 때, 쿠키를 함께 보낼 때 달라지는 것. 네 가지 질문에 스스로 답할 수 있게, 스프링 서버에 다른 출처의 요청과 사전 요청을 보내 보며 배워요.
minutes: 18
page: cors
verifiedBy:
  - verification/src/test/java/lab/devlessons/cors/CorsTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc6454.html
  - https://fetch.spec.whatwg.org/#http-cors-protocol
  - https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy
  - https://docs.spring.io/spring-framework/reference/web/webmvc-cors.html
  - https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html
updated: 2026-11-25
---

선수 레슨: HTTP 요청과 응답. 헤더와 OPTIONS 메서드를 알면 돼요.
