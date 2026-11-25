---
title: 스레드에 안전한 객체
unit: 운영체제와 동시성
order: 406
stage: 동시성
summary: 불변 객체는 왜 안전한지, 스레드에 가둔다는 것과 ThreadLocal은 무엇인지, 스레드 풀에서 ThreadLocal을 지우지 않으면 무슨 일이 생기는지, 여러 스레드가 함께 쓰는 맵은 무엇을 써야 하는지. 네 가지 질문에 스스로 답할 수 있게 락 없이 안전하게 만드는 세 방법을 돌려 봐요.
minutes: 22
page: thread-safe-objects
verifiedBy:
  - verification/src/test/java/lab/devlessons/threadsafe/ThreadSafeObjectsTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ThreadLocal.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html
  - https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html
updated: 2026-11-25
---

선수 레슨: 경쟁 상태와 동기화, 불변 객체. 원자성과 가시성, 불변 객체를 만드는 법을 알면 돼요.
