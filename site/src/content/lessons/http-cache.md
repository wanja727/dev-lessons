---
title: "HTTP 캐시"
unit: 네트워크와 웹
order: 511
stage: 기초
summary: HTTP 캐시가 무엇을 언제 다시 쓰는지, 시간이 지난 캐시를 ETag와 304로 확인하는 법, Cache-Control의 값을 고르는 법, CDN이 캐시를 다루는 법. 네 가지 질문에 스스로 답할 수 있게, 스프링 서버의 캐시 헤더와 304 응답을 확인하며 배워요.
minutes: 18
page: http-cache
verifiedBy:
  - verification/src/test/java/lab/devlessons/httpcache/HttpCacheTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9111.html
  - https://www.rfc-editor.org/rfc/rfc9110.html#section-13
  - https://www.rfc-editor.org/rfc/rfc8246.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-caching.html
updated: 2026-11-25
---

선수 레슨: HTTP 요청과 응답. 헤더와 상태 코드를 알면 돼요. 심화 레슨이에요.
