---
title: "MVCC"
unit: DB
order: 613
stage: DB와 운영
summary: 쓰는 중인 행을 읽는데 왜 기다리지 않는지, 각 트랜잭션은 어떤 버전을 보는지, 옛 버전은 언제 사라지고 긴 트랜잭션은 왜 문제인지, MVCC에서도 기다리거나 실패하는 것은 무엇인지. 네 가지 질문에 스스로 답할 수 있게 PostgreSQL 행의 숨은 열을 들여다보며 배워요.
minutes: 20
page: mvcc
verifiedBy:
  - verification/src/test/java/lab/devlessons/mvcc/MvccTest.java
sources:
  - https://www.postgresql.org/docs/16/mvcc-intro.html
  - https://www.postgresql.org/docs/16/ddl-system-columns.html
  - https://www.postgresql.org/docs/16/routine-vacuuming.html
  - https://www.postgresql.org/docs/16/runtime-config-client.html
updated: 2026-11-25
---

선수 레슨: 격리 수준과 이상 현상. READ COMMITTED와 REPEATABLE READ가 보는 값의 차이를 알면 돼요.
