---
title: 프로세스와 스레드
unit: 운영체제와 동시성
order: 401
stage: 동시성
summary: 프로세스와 스레드는 무엇이 다른지, 같은 프로세스의 스레드는 무엇을 함께 쓰고 따로 가지는지, 스레드는 어떻게 시작하는지, 메모리를 함께 쓰면 무엇이 문제인지. 네 가지 질문에 스스로 답할 수 있게 자식 JVM을 띄우고 두 스레드로 같은 변수를 올려 보며 배워요.
minutes: 20
page: process-and-thread
verifiedBy:
  - verification/src/test/java/lab/devlessons/processthread/ProcessAndThreadTest.java
sources:
  - https://docs.oracle.com/javase/tutorial/essential/concurrency/procthread.html
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-25
---

선수 레슨: JVM 메모리 구조. 객체는 힙에, 지역 변수는 스레드마다 있는 스택에 놓인다는 것을 알면 돼요.
