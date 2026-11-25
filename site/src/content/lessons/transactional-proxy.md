---
title: "@Transactional 동작 원리"
unit: 스프링
order: 710
stage: 트랜잭션과 JPA
summary: "@Transactional을 붙이면 누가 트랜잭션을 여닫는지, 같은 클래스 안의 호출에서 트랜잭션이 빠지는 이유, 어디에 붙여야 걸리는지, 프록시 없이 트랜잭션을 여는 TransactionTemplate. 메서드 안에서 트랜잭션이 열렸는지 직접 물어봐요."
minutes: 20
page: transactional-proxy
verifiedBy:
  - verification/src/test/java/lab/devlessons/txproxy/TxProxyLessonTest.java
  - verification/src/test/java/lab/devlessons/transaction/TransactionalProxyTest.java
  - verification/src/test/java/lab/devlessons/transaction/SelfInvocationTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-decl-explained.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html
updated: 2026-11-25
---

선수 레슨: AOP, 트랜잭션 기초.
