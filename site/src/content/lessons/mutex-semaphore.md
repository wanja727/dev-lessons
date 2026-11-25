---
title: 뮤텍스와 세마포어
unit: 운영체제와 동시성
order: 404
stage: 동시성
summary: 뮤텍스는 무엇이고 자바에서는 무엇으로 쓰는지, ReentrantLock은 synchronized로 못 하는 무엇을 하는지, 세마포어는 뮤텍스와 무엇이 다른지, 언제 무엇을 쓰는지. 네 가지 질문에 스스로 답할 수 있게 허가 3개인 세마포어에 스레드 10개를 보내 봐요.
minutes: 18
page: mutex-semaphore
verifiedBy:
  - verification/src/test/java/lab/devlessons/mutexsemaphore/MutexSemaphoreTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/ReentrantLock.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/locks/Lock.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Semaphore.html
  - https://resilience4j.readme.io/docs/bulkhead
updated: 2026-11-25
---

선수 레슨: 경쟁 상태와 동기화. 임계 영역과 synchronized를 알면 돼요.
