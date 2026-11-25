---
title: "의존 역전 원칙과 의존성 주입"
unit: 객체지향 설계와 패턴
order: 305
stage: 기초
summary: 구현 클래스를 안에서 직접 만들면 생기는 문제, 의존 역전 원칙에서 무엇이 뒤집히는지, 의존성 주입이 무엇을 쉽게 하는지, 두 말이 어떻게 다른지. 네 가지 질문에 스스로 답할 수 있게 회원 가입 알림 코드를 바꿔 가며 배워요.
minutes: 20
page: dip-di
verifiedBy:
  - verification/src/test/java/lab/devlessons/dipdi/DipDiTest.java
sources:
  - https://web.archive.org/web/20110714224327/http://www.objectmentor.com/resources/articles/dip.pdf
  - https://martinfowler.com/articles/injection.html
  - https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html
updated: 2026-11-25
---

선수 레슨: 단일 책임과 개방 폐쇄 원칙. 인터페이스와 구현 클래스, 바뀌는 부분을 인터페이스로 빼는 것을 알면 돼요.
