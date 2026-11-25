---
title: "스레드 풀과 커넥션 풀 크기"
unit: 시스템 설계와 아키텍처
order: 911
stage: 실무 시스템
summary: 톰캣 스레드와 HikariCP 커넥션이 맞물리는 방식, 커넥션 풀이 고갈될 때 생기는 일, 풀 고갈 데드락, 두 풀의 크기를 정하는 법. 네 가지 질문에 스스로 답할 수 있게 스레드와 커넥션 수를 바꿔 가며 재 봐요.
minutes: 20
page: pool-sizing
verifiedBy:
  - verification/src/test/java/lab/devlessons/poolsizing/PoolSizingTest.java
sources:
  - https://github.com/brettwooldridge/HikariCP
  - https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
  - https://www.postgresql.org/docs/16/runtime-config-connection.html
updated: 2026-11-25
---

선수 레슨: DB 커넥션과 커넥션 풀, 스레드 풀.
