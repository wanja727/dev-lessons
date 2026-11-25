---
title: "static과 초기화 순서"
unit: 자바 언어
order: 42
stage: 기초
summary: static이 붙은 것은 무엇에 딸려 있는지, static 필드와 static 블록은 언제 실행되는지, 객체를 만들 때 부모와 자식이 어떤 순서로 초기화되는지. 세 가지 질문에 스스로 답할 수 있게 단계마다 출력을 넣은 쿠폰 클래스로 배워요.
minutes: 18
page: static-initialization
verifiedBy:
  - verification/src/test/java/lab/devlessons/staticinit/StaticInitializationTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.1
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.5
  - https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
  - https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/autowired.html
updated: 2026-11-25
---

선수 레슨: 값과 참조. 클래스에 필드와 생성자를 만들고 `extends`로 상속해 본 적이 있으면 돼요.
