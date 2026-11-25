---
title: "트랜잭션 전파와 롤백 규칙"
unit: 스프링
order: 711
stage: 트랜잭션과 JPA
summary: "@Transactional 메서드가 다른 @Transactional 메서드를 부를 때 트랜잭션이 몇 개인지, 안쪽만 따로 커밋하거나 롤백하는 REQUIRES_NEW, 어떤 예외에 롤백하는지, 메서드 안에서 예외를 잡으면 어떻게 되는지. 조합마다 DB에 남은 행을 확인해요."
minutes: 20
page: tx-propagation
verifiedBy:
  - verification/src/test/java/lab/devlessons/txpropagation/TxPropagationLessonTest.java
  - verification/src/test/java/lab/devlessons/propagation/PropagationTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html
updated: 2026-11-25
---

선수 레슨: @Transactional 동작 원리.
