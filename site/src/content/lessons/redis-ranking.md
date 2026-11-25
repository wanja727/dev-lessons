---
title: "Redis"
unit: 시스템 설계와 아키텍처
order: 905
stage: 실무 시스템
summary: Redis의 자료구조, Sorted Set으로 만드는 랭킹, 여러 서버가 동시에 바꿔도 값이 맞는 이유와 맞지 않는 경우, Redis에 둔 데이터를 믿어도 되는지. 네 가지 질문에 스스로 답할 수 있게 실제 Redis에 명령을 보내 봐요.
minutes: 20
page: redis-ranking
verifiedBy:
  - verification/src/test/java/lab/devlessons/ranking/RedisRankingTest.java
  - verification/src/test/java/lab/devlessons/ranking/RedisAtomicityTest.java
sources:
  - https://redis.io/docs/latest/develop/data-types/
  - https://redis.io/docs/latest/develop/data-types/sorted-sets/
  - https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/
  - https://docs.spring.io/spring-data/redis/reference/redis/template.html
updated: 2026-11-25
---

선수 레슨: 캐시 전략과 무효화.
