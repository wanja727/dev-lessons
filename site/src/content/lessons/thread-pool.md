---
title: 스레드 풀과 ExecutorService
unit: 동시성·분산
order: 6
stage: 동시성
summary: 작업이 몰릴 때 스레드 풀은 core → 큐 → max → 거절 순서로 반응해요. 시뮬레이터로 직접 작업을 넣어 보고, Executors 팩토리와 Spring @Async 기본 설정의 숨은 한계, 종료와 예외 처리까지 배워요.
minutes: 30
page: thread-pool
verifiedBy:
  - verification/src/test/java/lab/devlessons/concurrency/ThreadPoolTest.java
  - verification/src/test/java/lab/devlessons/concurrency/ThreadPoolOutputTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-16
---

선수 레슨: 프로세스와 스레드.
