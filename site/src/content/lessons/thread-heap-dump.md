---
title: "스레드 덤프와 힙 덤프"
unit: 테스트와 운영
order: 910
stage: 실무 시스템
summary: 스레드 덤프에 보이는 것, 멈춘 서버의 원인을 덤프에서 찾는 법, 메모리를 붙잡는 것을 히스토그램과 힙 덤프로 찾는 법, 메모리 장애의 증거를 미리 남기는 법. 네 가지 질문에 스스로 답할 수 있게 데드락과 누수를 일부러 만든 자식 JVM으로 배워요.
minutes: 20
page: thread-heap-dump
verifiedBy:
  - verification/src/test/java/lab/devlessons/dump/ThreadHeapDumpTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.State.html
  - https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
  - https://docs.oracle.com/en/java/javase/21/troubleshoot/
updated: 2026-11-26
---

선수 레슨: 가비지 컬렉션, 데드락. 닿을 수 있는 객체는 치워지지 않는다는 것과 데드락이 생기는 조건을 알면 돼요.
