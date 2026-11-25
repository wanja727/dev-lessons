---
title: 낙관적 락과 비관적 락
unit: DB
order: 610
stage: DB와 운영
summary: 갱신 손실은 왜 생기고 언제 락이 필요한지, 낙관적 락과 비관적 락은 각각 어떻게 동작하는지, 둘을 어떻게 고르는지. 네 가지 질문에 스스로 답할 수 있게 재고 100개에 동시 주문을 넣으며 배워요.
minutes: 25
page: locking
verifiedBy:
  - verification/src/test/java/lab/devlessons/lock/LockingLessonTest.java
  - verification/src/test/java/lab/devlessons/lock/LockingTest.java
sources:
  - https://www.postgresql.org/docs/16/explicit-locking.html
  - https://www.postgresql.org/docs/16/sql-select.html
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
  - https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
updated: 2026-11-25
---

선수 레슨: 격리 수준과 이상 현상. 읽고 계산해서 쓰면 갱신 손실이 생긴다는 것을 알면 돼요.
