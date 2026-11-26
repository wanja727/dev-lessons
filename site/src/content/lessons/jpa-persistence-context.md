---
title: 영속성 컨텍스트
unit: JPA
order: 802
stage: 트랜잭션과 JPA
summary: 영속성 컨텍스트가 무엇이고 언제 생기는지, 같은 id를 두 번 조회하면 왜 SELECT가 한 번인지, INSERT는 언제 나가는지, save() 없이 왜 UPDATE가 나가는지, 트랜잭션 밖에서 바꾼 값은 왜 저장되지 않는지. 다섯 가지 질문에 스스로 답할 수 있게 책 제목을 바꾸며 배워요.
minutes: 25
page: jpa-persistence-context
verifiedBy:
  - verification/src/test/java/lab/devlessons/persistencecontext/PersistenceContextTest.java
  - verification/src/test/java/lab/devlessons/persistencecontext/PersistenceContextScopeTest.java
sources:
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
  - https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#flushing
updated: 2026-11-25
---

선수 레슨: ORM과 JPA. `@Transactional`이 메서드를 트랜잭션으로 감싼다는 것(트랜잭션 기초)을 알면 돼요.
