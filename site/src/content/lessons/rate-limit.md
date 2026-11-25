---
title: "요청 수 제한"
unit: 시스템 설계와 아키텍처
order: 913
stage: 실무 시스템
summary: 요청 수를 제한하는 이유, 고정 창과 경계에서 두 배가 통과하는 문제, 토큰 버킷, 제한을 어디에 두고 넘으면 무엇을 돌려주는지. 네 가지 질문에 스스로 답할 수 있게 Redis로 두 방식을 재 봐요.
minutes: 20
page: rate-limit
verifiedBy:
  - verification/src/test/java/lab/devlessons/ratelimit/RateLimitTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc6585
  - https://www.rfc-editor.org/rfc/rfc9110
  - https://redis.io/docs/latest/commands/incr/
  - https://bucket4j.com/
updated: 2026-11-25
---

선수 레슨: Redis.
