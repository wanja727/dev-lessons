---
title: "REST API 설계"
unit: 네트워크와 웹
order: 505
stage: 기초
summary: REST API에서 URL과 메서드를 나누는 법, 안전과 멱등을 따지는 이유, 상태 코드를 고르는 법, 에러 응답의 표준 모양(Problem Details). 네 가지 질문에 스스로 답할 수 있게, 상품 API에 같은 요청을 두 번씩 보내 보며 배워요.
minutes: 20
page: rest-api
verifiedBy:
  - verification/src/test/java/lab/devlessons/restdesign/RestDesignTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110.html
  - https://www.rfc-editor.org/rfc/rfc9457.html
  - https://ics.uci.edu/~fielding/pubs/dissertation/rest_arch_style.htm
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
updated: 2026-11-25
---

선수 레슨: HTTP 요청과 응답. 메서드와 상태 코드의 묶음을 알면 돼요.
