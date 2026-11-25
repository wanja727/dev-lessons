---
title: "동시 요청에서 재고 지키기"
unit: 시스템 설계와 아키텍처
order: 907
stage: 실무 시스템
summary: 재고를 확인하고 줄였는데 재고보다 많이 팔리는 이유, 조건부 UPDATE와 락, Redis로 줄일 때 조심할 것, 고르는 기준. 네 가지 질문에 스스로 답할 수 있게 실제 PostgreSQL과 Redis에 주문 100건을 동시에 넣어 봐요.
minutes: 25
page: stock-concurrency
verifiedBy:
  - verification/src/test/java/lab/devlessons/inventory/InventoryConcurrencyTest.java
  - verification/src/test/java/lab/devlessons/inventory/InventoryPracticeTest.java
sources:
  - https://www.postgresql.org/docs/16/explicit-locking.html
  - https://redis.io/docs/latest/develop/interact/programmability/eval-intro/
  - https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
  - https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
updated: 2026-11-25
---

선수 레슨: 낙관적 락과 비관적 락, Redis.
