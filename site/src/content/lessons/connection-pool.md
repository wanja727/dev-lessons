---
title: "DB 커넥션과 커넥션 풀"
unit: DB
order: 2
stage: 기초
summary: 프로그램이 DB와 대화하려면 연결(커넥션)이 필요해요. 연결을 매번 새로 만들면 왜 느린지, 커넥션 풀이 연결을 어떻게 빌려주고 돌려받는지, 풀이 모자라면 요청이 어떻게 기다리다 실패하는지 직접 재고 그려 보며 배워요.
minutes: 25
page: connection-pool
verifiedBy:
  - verification/src/test/java/lab/devlessons/connpool/ConnectionPoolTest.java
sources:
  - https://github.com/brettwooldridge/HikariCP
  - https://docs.spring.io/spring-boot/reference/data/sql.html
  - https://www.postgresql.org/docs/16/connect-estab.html
updated: 2026-11-16
---

선수 레슨: 트랜잭션 기초.
