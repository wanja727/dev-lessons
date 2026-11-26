---
title: "스프링 배치"
unit: 스프링
order: 715
stage: 실무 시스템
summary: 스프링 배치가 대량 데이터를 청크 단위로 처리하고 커밋하는 방식, 중간에 실패했을 때 어디부터 다시 하는지, 잘못된 데이터와 이미 끝난 Job을 다루는 법. 25건을 청크 10으로 돌리다 실패시키고 다시 시작해 봐요.
minutes: 16
page: spring-batch
verifiedBy:
  - verification/src/test/java/lab/devlessons/batch/SpringBatchLessonTest.java
  - verification/src/test/java/lab/devlessons/batch/SpringBatchTest.java
sources:
  - https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html
  - https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/restart.html
  - https://docs.spring.io/spring-boot/how-to/batch.html
updated: 2026-11-26
---

선수 레슨: 트랜잭션 전파와 롤백 규칙.
