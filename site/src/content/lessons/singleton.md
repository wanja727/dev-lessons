---
title: "싱글톤 패턴"
unit: 객체지향 설계와 패턴
order: 311
stage: 기초
summary: 싱글톤을 만드는 네 가지 방법, 여러 스레드에서 하나만 만들어지는지, 싱글톤이 테스트하기 어려운 이유, 스프링의 싱글톤과 무엇이 다른지. 네 가지 질문에 스스로 답할 수 있게 설정 저장소를 두 스레드가 동시에 꺼내 보며 배워요.
minutes: 20
page: singleton
verifiedBy:
  - verification/src/test/java/lab/devlessons/singleton/SingletonTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4.2
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.9
  - https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html#beans-factory-scopes-singleton
updated: 2026-11-25
---

선수 레슨: 의존 역전 원칙과 의존성 주입, static과 초기화 순서. static 필드와 클래스 초기화, 생성자로 주입받는 것을 알면 돼요.
