---
title: "동기화: synchronized, volatile, 원자적 연산"
unit: 동시성·분산
summary: 동시성 문제를 원자성과 가시성으로 나눠 보면 synchronized, volatile, AtomicInteger가 각각 무엇을 막는지 분명해져요. 가시성을 비유로 직접 조작하고, 상황별로 맞는 도구를 골라 봐요.
minutes: 30
page: synchronization
verifiedBy:
  - verification/src/test/java/lab/devlessons/concurrency/SynchronizationTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.oracle.com/javase/tutorial/essential/concurrency/sync.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/package-summary.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html
updated: 2026-11-16
---

선수 레슨: 프로세스와 스레드.
