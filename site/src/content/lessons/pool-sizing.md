---
title: 커넥션 풀과 톰캣 스레드 풀 크기
unit: DB
order: 29
stage: 실무 시스템
summary: 요청 스레드 200개와 커넥션 10개가 맞물리는 방식을 보고, 커넥션을 쥔 채 REQUIRES_NEW로 하나 더 요청할 때 생기는 풀 고갈 데드락을 재현해요. 리틀의 법칙과 HikariCP 공식으로 크기를 정해요.
minutes: 30
page: pool-sizing
verifiedBy:
  - verification/src/test/java/lab/devlessons/poolsizing/PoolSizingTest.java
sources:
  - https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-17
---

선수 레슨: DB 커넥션과 커넥션 풀, 스레드 풀과 ExecutorService.
