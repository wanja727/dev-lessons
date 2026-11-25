---
title: "래퍼 클래스와 오토박싱"
unit: 자바 언어
order: 39
stage: 기초
summary: int가 있는데 Integer가 왜 있는지, 자바가 둘을 언제 몰래 바꿔 주는지, Integer를 ==로 비교하면 왜 어떤 때만 true인지, null인 래퍼가 어디서 예외를 내는지. 네 가지 질문에 스스로 답할 수 있게 회원 번호 127과 128을 비교하며 배워요.
minutes: 20
page: wrapper-autoboxing
verifiedBy:
  - verification/src/test/java/lab/devlessons/wrapper/WrapperAutoboxingTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.7
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Long.html#valueOf(long)
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/requestparam.html
updated: 2026-11-25
---

선수 레슨: 값과 참조. 참조형 변수의 `==`가 같은 객체인지를 본다는 것을 알면 돼요.
