---
title: "트랜잭셔널 아웃박스"
unit: 시스템 설계와 아키텍처
order: 918
stage: 실무 시스템
summary: 주문 저장과 메시지 발행이 어긋나는 이유, 커밋 뒤에 보내도 부족한 이유, 같은 트랜잭션의 테이블에 적어 두고 따로 보내는 아웃박스, 아웃박스가 남기는 중복과 운영 문제. 네 가지 질문에 스스로 답할 수 있게 가짜 브로커를 죽였다 살리며 세어 봐요.
minutes: 25
page: transactional-outbox
verifiedBy:
  - verification/src/test/java/lab/devlessons/outbox/TransactionalOutboxTest.java
sources:
  - https://microservices.io/patterns/data/transactional-outbox.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
  - https://www.postgresql.org/docs/16/sql-select.html
  - https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html
updated: 2026-11-25
---

선수 레슨: 트랜잭션 전파와 롤백 규칙, 메시지 큐.
