---
title: 외부 API 타임아웃, 재시도, 서킷 브레이커
unit: Spring
order: 28
stage: 실무 시스템
summary: PG사가 느려지면 우리 서버까지 멈추는 장애 전파를 재현해요. 타임아웃은 실패가 아니라 "모름"이라는 것, 멱등 키 없는 재시도의 이중 결제, 지수 백오프와 서킷 브레이커의 세 상태를 진짜 HTTP 서버로 확인해요.
minutes: 35
page: resilience
verifiedBy:
  - verification/src/test/java/lab/devlessons/resilience/ResilienceTest.java
sources:
  - https://resilience4j.readme.io/docs/circuitbreaker
  - https://resilience4j.readme.io/docs/retry
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html
  - https://docs.spring.io/spring-framework/reference/integration/rest-clients.html
updated: 2026-11-17
---

선수 레슨: 멱등성, 스레드 풀과 ExecutorService.
