---
title: "타임아웃, 재시도, 서킷 브레이커"
unit: 시스템 설계와 아키텍처
order: 910
stage: 실무 시스템
summary: 타임아웃이 필요한 이유와 타임아웃이 알려 주지 않는 것, 재시도를 해도 되는 때와 간격, 서킷 브레이커가 장애를 끊는 방식, 셋을 함께 쓰는 법. 네 가지 질문에 스스로 답할 수 있게 느린 결제 서버를 흉내 내 재 봐요.
minutes: 25
page: resilience
verifiedBy:
  - verification/src/test/java/lab/devlessons/resilience/ResilienceTest.java
sources:
  - https://resilience4j.readme.io/docs/circuitbreaker
  - https://resilience4j.readme.io/docs/retry
  - https://resilience4j.readme.io/docs/getting-started-3
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpRequest.Builder.html
updated: 2026-11-25
---

선수 레슨: 멱등성과 중복 요청 방지, 스레드 풀.
