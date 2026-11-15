---
title: "@Transactional 동작 원리와 내부 호출"
unit: Spring
summary: 트랜잭션을 여는 건 어노테이션이 아니라 프록시예요. 호출 경로를 한 단계씩 따라가며 내부 호출에서 트랜잭션이 빠지는 이유를 보고, 해결 방법 세 가지의 트랜잭션 범위 차이까지 배워요.
minutes: 30
page: transactional-proxy
verifiedBy:
  - verification/src/test/java/lab/devlessons/transaction/TransactionalProxyTest.java
  - verification/src/test/java/lab/devlessons/transaction/SelfInvocationTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
  - https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
updated: 2026-11-16
---

선수 레슨: 영속성 컨텍스트(변경 감지).
