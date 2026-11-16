---
title: 트랜잭셔널 아웃박스
unit: 동시성·분산
order: 24
stage: 실무 시스템
summary: DB 저장과 메시지 발행이 어긋나는 이중 쓰기 문제를 재현해요. 트랜잭션 안 발행은 유령 메시지를, 커밋 뒤 발행은 메시지 유실을 만들고, 아웃박스 테이블과 릴레이가 둘 다 막는 대신 중복을 남긴다는 걸 확인해요.
minutes: 35
page: transactional-outbox
verifiedBy:
  - verification/src/test/java/lab/devlessons/outbox/TransactionalOutboxTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/event/TransactionalEventListener.html
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html
  - https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
updated: 2026-11-17
---

선수 레슨: @Transactional 동작 원리, 트랜잭션 전파.
