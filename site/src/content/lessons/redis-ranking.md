---
title: Redis 자료구조와 랭킹
unit: DB
order: 31
stage: 실무 시스템
summary: Redis의 다섯 가지 자료구조와 쓰임을 보고, Sorted Set으로 실시간 XP 랭킹을 만들어요. 동점일 때의 순서와 시간을 섞는 법, 명령을 하나씩 실행하는 원자성, 일간·주간 랭킹을 실제 Redis로 확인해요.
minutes: 30
page: redis-ranking
verifiedBy:
  - verification/src/test/java/lab/devlessons/ranking/RedisRankingTest.java
sources:
  - https://redis.io/docs/latest/develop/data-types/
  - https://redis.io/docs/latest/develop/data-types/sorted-sets/
  - https://redis.io/docs/latest/commands/zrevrange/
  - https://redis.io/docs/latest/commands/zincrby/
  - https://redis.io/docs/latest/commands/zunionstore/
updated: 2026-11-17
---

선수 레슨: 캐시 전략과 무효화.
