---
title: "트랜잭션 기초"
unit: DB
summary: 계좌 이체 중간에 오류가 나면 돈은 어디로 갈까요? 여러 SQL을 "전부 아니면 전무"로 묶는 트랜잭션, 커밋과 롤백, 자동 커밋, 커밋 전 변경이 남에게 보이는지를 직접 실행하며 배워요. 스프링·JPA 트랜잭션 레슨의 출발점이에요.
minutes: 25
page: tx-basics
verifiedBy:
  - verification/src/test/java/lab/devlessons/txbasics/TransactionBasicsTest.java
sources:
  - https://www.postgresql.org/docs/16/tutorial-transactions.html
  - https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
updated: 2026-11-16
---

선수 레슨 없음. 자바 기초와 SQL(SELECT, UPDATE)만 알면 돼요.
