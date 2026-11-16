---
title: 캐시 전략과 무효화
unit: DB
order: 27
stage: 실무 시스템
summary: cache-aside와 write-through, TTL을 배우고, 캐시를 먼저 지우면 옛 값이 다시 캐시되는 경쟁과 만료 순간 DB로 몰리는 캐시 스탬피드를 실제 Redis로 재현해요.
minutes: 30
page: cache-strategy
verifiedBy:
  - verification/src/test/java/lab/devlessons/cache/CacheStrategyTest.java
sources:
  - https://redis.io/docs/latest/commands/set/
  - https://redis.io/docs/latest/commands/expire/
  - https://docs.spring.io/spring-framework/reference/integration/cache.html
  - https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Strategies.html
updated: 2026-11-17
---

선수 레슨 없음.
