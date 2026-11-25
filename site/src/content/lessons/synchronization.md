---
title: 경쟁 상태와 동기화
unit: 운영체제와 동시성
order: 403
stage: 동시성
summary: 경쟁 상태는 무엇이고 어떤 코드에서 생기는지, 원자성과 가시성은 무엇이고 volatile은 어느 것을 막는지, synchronized와 AtomicInteger는 어떻게 막는지. 네 가지 질문에 스스로 답할 수 있게 같은 카운터를 네 가지로 바꿔 두 스레드로 올려 봐요.
minutes: 22
page: synchronization
verifiedBy:
  - verification/src/test/java/lab/devlessons/synchronization/SynchronizationTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.oracle.com/javase/tutorial/essential/concurrency/sync.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/package-summary.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/StringBuilder.html
updated: 2026-11-25
---

선수 레슨: 프로세스와 스레드. 스레드가 힙과 static 필드를 함께 쓰고, count++가 겹치면 갱신이 사라진다는 것을 알면 돼요.
