---
title: "스키마 변경과 마이그레이션"
unit: DB
order: 616
stage: DB와 운영
summary: 마이그레이션 도구는 무엇을 해 주는지, 테이블을 고치면 왜 서비스가 멈추는지, 큰 테이블에 제약과 인덱스는 어떻게 더하는지, 열 이름을 바꾸거나 지울 때는 어떻게 하는지. 네 가지 질문에 스스로 답할 수 있게 회원 테이블을 서비스를 멈추지 않고 고쳐 보며 배워요.
minutes: 20
page: schema-migration
verifiedBy:
  - verification/src/test/java/lab/devlessons/migration/SchemaMigrationTest.java
sources:
  - https://www.postgresql.org/docs/16/sql-altertable.html
  - https://www.postgresql.org/docs/16/explicit-locking.html
  - https://www.postgresql.org/docs/16/sql-createindex.html
  - https://documentation.red-gate.com/flyway/flyway-concepts/migrations
  - https://docs.spring.io/spring-boot/how-to/data-initialization.html
updated: 2026-11-25
---

선수 레슨: 트랜잭션 기초. 인덱스와 실행계획, 낙관적 락과 비관적 락을 알면 더 잘 읽혀요.
