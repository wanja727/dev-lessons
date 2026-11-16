---
title: 격리 수준과 이상 현상
unit: DB
order: 19
stage: DB와 운영
summary: 두 트랜잭션을 한 단계씩 번갈아 실행하며 Non-Repeatable Read, Phantom Read, 갱신 손실이 언제 생기는지 봐요. 모든 결과는 PostgreSQL 16에서 테스트로 확인했고, MySQL과의 차이도 정리했어요.
minutes: 30
page: isolation-level
verifiedBy:
  - verification/src/test/java/lab/devlessons/isolation/IsolationLevelTest.java
sources:
  - https://www.postgresql.org/docs/16/transaction-iso.html
  - https://dev.mysql.com/doc/refman/8.4/en/innodb-transaction-isolation-levels.html
updated: 2026-11-16
---

선수 레슨 없음.
