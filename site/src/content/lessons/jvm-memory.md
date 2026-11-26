---
title: "JVM 메모리 구조"
unit: 자바 언어
order: 114
stage: 기초
summary: JVM이 메모리를 어떤 영역으로 나눠 쓰는지, 메서드를 부르면 스택에서 무슨 일이 생기는지, 객체는 어디에 놓이는지, OutOfMemoryError와 StackOverflowError는 무엇이 다른지. 네 가지 질문에 스스로 답할 수 있게 두 에러를 일부러 일으켜 보며 배워요.
minutes: 22
page: jvm-memory
verifiedBy:
  - verification/src/test/java/lab/devlessons/jvmmemory/JvmMemoryTest.java
sources:
  - https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5
  - https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.6
  - https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
  - https://docs.oracle.com/en/java/javase/21/gctuning/ergonomics1.html
updated: 2026-11-25
---

선수 레슨: 자바 프로그램이 실행되는 과정, 값과 참조. 변수에는 객체가 아니라 참조가 들어 있다는 것을 알면 돼요.
