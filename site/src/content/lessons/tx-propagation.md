---
title: "트랜잭션 전파와 롤백 규칙"
unit: Spring
order: 15
stage: 트랜잭션과 JPA
summary: 트랜잭션 안에서 다른 @Transactional 메서드를 부르면 합류할까, 새로 열까? REQUIRED와 REQUIRES_NEW의 차이, 예외를 잡았는데도 UnexpectedRollbackException이 나는 이유, 체크 예외가 롤백되지 않는 규칙을 시나리오별로 확인해요.
minutes: 30
page: tx-propagation
verifiedBy:
  - verification/src/test/java/lab/devlessons/propagation/PropagationTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html
updated: 2026-11-15
---

선수 레슨: @Transactional 동작 원리와 내부 호출(프록시).
