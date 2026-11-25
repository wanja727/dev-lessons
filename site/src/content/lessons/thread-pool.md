---
title: 스레드 풀
unit: 운영체제와 동시성
order: 407
stage: 동시성
summary: 스레드를 매번 만들지 않고 풀을 쓰는 이유, 작업이 몰리면 풀이 받는 순서, Executors가 만드는 풀의 함정, 풀을 끝내는 법과 작업의 예외가 가는 곳. 네 가지 질문에 스스로 답할 수 있게 작은 풀에 작업을 하나씩 넣으며 스레드와 큐를 세어 봐요.
minutes: 22
page: thread-pool
verifiedBy:
  - verification/src/test/java/lab/devlessons/threadpool/ThreadPoolTest.java
  - verification/src/test/java/lab/devlessons/threadpool/ThreadPoolOutputTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html
  - https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html
updated: 2026-11-25
---

선수 레슨: 프로세스와 스레드. 스레드를 start()로 시작하고 join()으로 기다리는 것을 알면 돼요.
