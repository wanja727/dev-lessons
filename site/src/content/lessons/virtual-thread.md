---
title: 가상 스레드
unit: 운영체제와 동시성
order: 412
stage: 동시성
summary: 가상 스레드는 플랫폼 스레드와 무엇이 다른지, 블로킹 호출을 만나면 무슨 일이 생기는지, 맞는 일과 맞지 않는 일은 무엇인지. 세 가지 질문에 스스로 답할 수 있게 기다리는 작업 2000개를 두 방식으로 돌리고 synchronized의 고정을 확인해 봐요.
minutes: 18
page: virtual-thread
verifiedBy:
  - verification/src/test/java/lab/devlessons/virtualthread/VirtualThreadTest.java
sources:
  - https://openjdk.org/jeps/444
  - https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
  - https://openjdk.org/jeps/491
  - https://docs.spring.io/spring-boot/reference/features/spring-application.html
updated: 2026-11-25
---

선수 레슨: 스레드 풀, 동기와 비동기, 블로킹과 논블로킹. 블로킹된 스레드는 다른 일을 못 해 동시에 기다리는 수만큼 스레드가 필요하다는 것을 알면 돼요.
