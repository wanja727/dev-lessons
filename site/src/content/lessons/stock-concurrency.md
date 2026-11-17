---
title: 재고 차감 동시성 (DB 락 vs Redis)
unit: 동시성·분산
order: 26
stage: 실무 시스템
summary: 재고 50개에 동시 주문 100건을 넣어 초과 판매를 재현해요. 조건부 UPDATE, 비관적 락, 낙관적 락, Redis DECR와 Lua 스크립트를 실제 결과로 비교하고 상황별로 고르는 기준을 배워요.
minutes: 35
page: stock-concurrency
verifiedBy:
  - verification/src/test/java/lab/devlessons/inventory/InventoryConcurrencyTest.java
sources:
  - https://www.postgresql.org/docs/16/explicit-locking.html
  - https://www.postgresql.org/docs/16/sql-update.html
  - https://redis.io/docs/latest/commands/decr/
  - https://redis.io/docs/latest/commands/eval/
  - https://redis.io/docs/latest/develop/programmability/eval-intro/
updated: 2026-11-17
---

선수 레슨: 낙관적 락 vs 비관적 락, 멱등성.
