---
title: "트랜잭션 기초"
unit: DB
order: 1
stage: 기초
summary: 트랜잭션이 왜 필요한지, 커밋과 롤백이 무엇을 하는지, 커밋 전 변경이 남에게 보이는지, 코드에서는 어떻게 쓰는지, ACID가 무엇인지. 다섯 가지 질문에 스스로 답할 수 있게 계좌 이체 하나로 배워요.
minutes: 20
page: tx-basics
verifiedBy:
  - verification/src/test/java/lab/devlessons/txbasics/TransactionBasicsTest.java
sources:
  - https://www.postgresql.org/docs/16/tutorial-transactions.html
  - https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
  - https://www.postgresql.org/docs/16/explicit-locking.html
updated: 2026-11-22
---

선수 레슨 없음. 자바 기초와 SQL(SELECT, UPDATE)만 알면 돼요.
