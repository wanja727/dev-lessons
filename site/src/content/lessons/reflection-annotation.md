---
title: "리플렉션과 어노테이션"
unit: 자바 언어
order: 53
stage: 기초
summary: 리플렉션이 무엇인지, 리플렉션으로 무엇을 할 수 있고 무엇을 치르는지, 어노테이션을 실행 중에 읽으려면 무엇이 필요한지, 스프링은 어노테이션을 어떻게 읽어서 일하는지. 네 가지 질문에 스스로 답할 수 있게 의존성 주입을 흉내 낸 작은 컨테이너를 직접 만들며 배워요.
minutes: 22
page: reflection-annotation
verifiedBy:
  - verification/src/test/java/lab/devlessons/reflection/ReflectionAnnotationTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-9.html#jls-9.6
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/annotation/Retention.html
  - https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
updated: 2026-11-25
---

선수 레슨: 상속과 다형성. `getClass()`가 변수의 타입이 아니라 실제 객체의 클래스를 준다는 것을 알면 돼요.
