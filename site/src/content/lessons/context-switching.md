---
title: 컨텍스트 스위칭
unit: 운영체제와 동시성
order: 402
stage: 동시성
summary: 코어보다 많은 스레드는 어떻게 모두 실행되는지, 컨텍스트 스위칭은 무엇을 바꾸는지, 왜 비용이 드는지, 스레드를 늘리면 늘 빨라지는지. 네 가지 질문에 스스로 답할 수 있게 계산하는 일과 기다리는 일을 스레드 수를 바꿔 가며 재 봐요.
minutes: 18
page: context-switching
verifiedBy:
  - verification/src/test/java/lab/devlessons/contextswitch/ContextSwitchingTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runtime.html
  - https://docs.kernel.org/scheduler/sched-design-CFS.html
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-25
---

선수 레슨: 프로세스와 스레드. 스레드가 스택과 실행 위치를 따로 가진다는 것을 알면 돼요.
