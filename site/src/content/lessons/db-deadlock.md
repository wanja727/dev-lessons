---
title: "DB 데드락"
unit: DB
order: 611
stage: DB와 운영
summary: DB 데드락은 어떻게 생기는지, DB는 데드락을 어떻게 푸는지, 줄이려면 어떻게 하는지, 데드락이 나면 앱은 어떻게 하는지. 네 가지 질문에 스스로 답할 수 있게 서로에게 동시에 돈을 보내는 이체로 데드락을 직접 만들어 보며 배워요.
minutes: 20
page: db-deadlock
verifiedBy:
  - verification/src/test/java/lab/devlessons/dbdeadlock/DbDeadlockTest.java
sources:
  - https://www.postgresql.org/docs/16/explicit-locking.html
  - https://www.postgresql.org/docs/16/runtime-config-locks.html
  - https://docs.spring.io/spring-framework/reference/data-access/dao.html
updated: 2026-11-25
---

선수 레슨: 낙관적 락과 비관적 락. UPDATE와 FOR UPDATE가 행을 잠그고 다른 트랜잭션이 기다린다는 것을 알면 돼요.
