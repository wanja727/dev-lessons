---
title: 프로세스와 스레드
unit: 운영체제
order: 5
stage: 동시성
summary: 스레드는 메모리를 공유한다는 한 문장이 무슨 일을 일으키는지. 메모리 구조를 비교하고, count++의 경쟁 상태를 직접 한 단계씩 실행해 보고, Spring 서버의 요청 스레드와 연결해요.
minutes: 25
page: process-and-thread
verifiedBy:
  - verification/src/test/java/lab/devlessons/concurrency/ProcessAndThreadTest.java
sources:
  - https://docs.oracle.com/javase/tutorial/essential/concurrency/procthread.html
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-16
---

Java 동시성 흐름의 첫 레슨. 선수 레슨 없음.
