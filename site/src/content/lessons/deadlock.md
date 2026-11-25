---
title: 데드락
unit: 운영체제와 동시성
order: 405
stage: 동시성
summary: 데드락은 어떻게 생기는지, 생기려면 어떤 네 조건이 모두 있어야 하는지, 어떻게 막는지, 났는지 어떻게 알아보는지. 네 가지 질문에 스스로 답할 수 있게 계좌 이체 두 개로 데드락을 일부러 만들고 락 순서를 정해 풀어 봐요.
minutes: 20
page: deadlock
verifiedBy:
  - verification/src/test/java/lab/devlessons/deadlock/DeadlockTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.management/java/lang/management/ThreadMXBean.html
  - https://docs.oracle.com/en/java/javase/21/docs/specs/man/jstack.html
  - https://www.postgresql.org/docs/current/explicit-locking.html
updated: 2026-11-25
---

선수 레슨: 뮤텍스와 세마포어. synchronized와 ReentrantLock, tryLock을 알면 돼요.
