---
title: 분산 트랜잭션과 사가
unit: 동시성·분산
order: 25
stage: 실무 시스템
summary: 서비스마다 DB가 따로일 때 여러 단계를 하나처럼 맞추는 법. 2PC를 잘 쓰지 않는 이유를 보고, 재고 차감 실패로 보상 트랜잭션이 역순으로 실행되는 사가를 오케스트레이션과 코레오그래피로 확인해요.
minutes: 35
page: saga
verifiedBy:
  - verification/src/test/java/lab/devlessons/saga/SagaTest.java
sources:
  - https://www.postgresql.org/docs/16/sql-prepare-transaction.html
  - https://www.postgresql.org/docs/16/runtime-config-resource.html
  - https://learn.microsoft.com/en-us/azure/architecture/patterns/saga
updated: 2026-11-17
---

선수 레슨: 트랜잭셔널 아웃박스.
