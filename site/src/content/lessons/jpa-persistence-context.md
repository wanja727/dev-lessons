---
title: 영속성 컨텍스트
unit: JPA
summary: save()를 안 불렀는데 UPDATE가 나가는 이유. 1차 캐시, 쓰기 지연, 변경 감지를 작업 공간 시뮬레이터로 직접 들여다보고, 준영속과 readOnly 같은 함정까지 배워요.
minutes: 30
page: jpa-persistence-context
verifiedBy:
  - verification/src/test/java/lab/devlessons/persistencecontext/PersistenceContextTest.java
  - verification/src/test/java/lab/devlessons/persistencecontext/PersistenceContextScopeTest.java
sources:
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1
  - https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#pc
  - https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#flushing
updated: 2026-11-15
---

JPA 단원의 첫 레슨. 선수 레슨 없음.
