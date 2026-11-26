---
title: "자바 프로그램이 실행되는 과정"
unit: 자바 언어
order: 101
stage: 기초
summary: 자바 소스가 바이트코드가 되어 JVM에서 실행되기까지, JVM과 JRE와 JDK의 차이, 클래스가 읽히는 때, JIT 때문에 켠 직후가 느린 이유. 네 가지 질문에 스스로 답할 수 있게, 직접 컴파일한 클래스 파일과 실행 결과로 배워요.
minutes: 20
page: java-execution
verifiedBy:
  - verification/src/test/java/lab/devlessons/javaexecution/JavaExecutionTest.java
sources:
  - https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
  - https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
  - https://docs.spring.io/spring-boot/specification/executable-jar/nested-jars.html
  - https://docs.gradle.org/current/userguide/toolchains.html
updated: 2026-11-25
---

선수 레슨 없음. `main` 메서드가 있는 클래스를 한 번 실행해 본 적만 있으면 돼요.
