---
title: "중첩 클래스와 익명 클래스"
unit: 자바 언어
order: 116
stage: 기초
summary: 클래스 안에 클래스를 선언하는 네 가지 방법, 내부 클래스와 static 중첩 클래스의 차이, 내부 클래스가 바깥 객체를 붙잡을 때 생기는 문제, 익명 클래스와 람다의 차이. 네 가지 질문에 스스로 답할 수 있게 주문과 주문 상품과 리스너로 배워요.
minutes: 20
page: nested-class
verifiedBy:
  - verification/src/test/java/lab/devlessons/nested/NestedClassTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.1.3
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.9.5
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.27.2
  - https://bugs.openjdk.org/browse/JDK-8271623
updated: 2026-11-25
---

선수 레슨: 상속과 다형성, 람다와 함수형 인터페이스. 인터페이스를 구현해 봤고 람다가 함수형 인터페이스의 구현이라는 것을 알면 돼요.
