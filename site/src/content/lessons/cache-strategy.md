---
title: "캐시 전략과 무효화"
unit: 시스템 설계와 아키텍처
order: 904
stage: 실무 시스템
summary: 캐시가 무엇을 빠르게 하는지와 cache-aside, 값이 바뀔 때 캐시를 지우는 순서, 만료 시간과 크기 한도, 캐시 스탬피드. 네 가지 질문에 스스로 답할 수 있게 실제 PostgreSQL과 Redis로 옛 가격이 남는 순간을 재현해 봐요.
minutes: 25
page: cache-strategy
verifiedBy:
  - verification/src/test/java/lab/devlessons/cache/CacheStrategyTest.java
  - verification/src/test/java/lab/devlessons/cache/CachePracticeTest.java
sources:
  - https://redis.io/docs/latest/commands/set/
  - https://redis.io/docs/latest/develop/reference/eviction/
  - https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html
  - https://docs.spring.io/spring-boot/reference/io/caching.html
updated: 2026-11-25
---

선수 레슨: 서버를 늘리는 방법.
