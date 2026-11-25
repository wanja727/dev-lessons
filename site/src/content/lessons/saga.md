---
title: "분산 트랜잭션과 사가"
unit: 시스템 설계와 아키텍처
order: 919
stage: 실무 시스템
summary: 여러 서비스에 걸친 일을 2PC로 묶지 않는 이유, 사가가 보상으로 되돌리는 방식, 오케스트레이션과 코레오그래피, 사가에 격리가 없어 생기는 일과 보상의 멱등성. 네 가지 질문에 스스로 답할 수 있게 주문, 결제, 재고 서비스로 케이크 주문을 흘려 봐요.
minutes: 25
page: saga
verifiedBy:
  - verification/src/test/java/lab/devlessons/saga/SagaTest.java
sources:
  - https://microservices.io/patterns/data/saga.html
  - https://www.postgresql.org/docs/16/sql-prepare-transaction.html
  - https://www.postgresql.org/docs/16/runtime-config-resource.html
updated: 2026-11-25
---

선수 레슨: 트랜잭셔널 아웃박스.
