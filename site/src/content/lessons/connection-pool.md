---
title: "DB 커넥션과 커넥션 풀"
unit: DB
order: 606
stage: 기초
summary: 커넥션을 매번 새로 만들면 왜 안 되는지, 커넥션 풀이 어떻게 빌려주고 돌려받는지, 연결이 모두 쓰이고 있으면 다음 요청이 어떻게 되는지, 트랜잭션이 연결을 얼마나 오래 쥐는지. 네 가지 질문에 스스로 답할 수 있게 배워요.
minutes: 20
page: connection-pool
verifiedBy:
  - verification/src/test/java/lab/devlessons/connpool/ConnectionPoolTest.java
  - verification/src/test/java/lab/devlessons/connpool/ConnectionPoolPracticeTest.java
sources:
  - https://github.com/brettwooldridge/HikariCP
  - https://docs.spring.io/spring-boot/reference/data/sql.html
  - https://www.postgresql.org/docs/16/connect-estab.html
  - https://www.postgresql.org/docs/16/runtime-config-connection.html
updated: 2026-11-26
---

선수 레슨: 트랜잭션 기초.
