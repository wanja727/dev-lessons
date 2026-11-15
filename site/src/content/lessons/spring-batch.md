---
title: "스프링 배치: 청크 처리와 재시작"
unit: Spring
summary: 25건을 청크 10으로 처리하다 17번째에서 실패하면 무엇이 남고, 재시작하면 어디서부터 할까요? 시뮬레이터로 청크 커밋과 재시작을 직접 돌려 보고 JobInstance, ExecutionContext, skip까지 배워요.
minutes: 35
page: spring-batch
verifiedBy:
  - verification/src/test/java/lab/devlessons/batch/SpringBatchTest.java
sources:
  - https://docs.spring.io/spring-batch/reference/domain.html
  - https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html
  - https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing/restart.html
updated: 2026-11-16
---

선수 레슨: @Transactional 동작 원리.
