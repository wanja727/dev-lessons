---
title: 낙관적 락 vs 비관적 락
unit: 동시성·분산
order: 20
stage: DB와 운영
summary: 두 주문이 같은 재고를 동시에 줄일 때 락 없음, 낙관적 락, 재시도, 비관적 락이 각각 어떻게 동작하는지 단계별로 따라가고, 충돌 빈도에 따른 선택 기준을 배워요.
minutes: 30
page: locking
verifiedBy:
  - verification/src/test/java/lab/devlessons/lock/LockingTest.java
sources:
  - https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
  - https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html#locking
  - https://www.postgresql.org/docs/16/explicit-locking.html
updated: 2026-11-16
---

선수 레슨: 격리 수준과 이상 현상, 영속성 컨텍스트.
