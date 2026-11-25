---
title: 격리 수준과 이상 현상
unit: DB
order: 609
stage: DB와 운영
summary: 동시에 도는 트랜잭션이 서로 무엇을 볼 수 있는지, 격리 수준 네 가지가 각각 무엇을 막는지, READ COMMITTED와 REPEATABLE READ에서 무엇이 보이고 무엇이 실패하는지, 갱신 손실은 격리 수준으로 막히는지. 다섯 가지 질문에 스스로 답할 수 있게 연결 두 개로 문장 순서를 맞춰 실행하며 배워요.
minutes: 25
page: isolation-level
verifiedBy:
  - verification/src/test/java/lab/devlessons/isolation/IsolationLessonTest.java
  - verification/src/test/java/lab/devlessons/isolation/IsolationLevelTest.java
sources:
  - https://www.postgresql.org/docs/16/transaction-iso.html
  - https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
updated: 2026-11-25
---

선수 레슨: 트랜잭션 기초. 커밋과 롤백, 커밋 전 변경은 다른 연결에 보이지 않는다는 것을 알면 돼요.
