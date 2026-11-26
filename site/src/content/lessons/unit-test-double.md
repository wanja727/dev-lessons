---
title: "단위 테스트와 테스트 더블"
unit: 테스트와 운영
order: 1002
stage: 실무 시스템
summary: 단위 테스트가 확인하는 것, DB와 메일 같은 협력 객체를 테스트 더블로 바꾸는 법, 목과 스텁의 차이, 목을 지나치게 쓰면 생기는 문제. 네 가지 질문에 스스로 답할 수 있게 회원 가입 서비스로 배워요.
minutes: 20
page: unit-test-double
verifiedBy:
  - verification/src/test/java/lab/devlessons/unittest/UnitTestDoubleTest.java
  - verification/src/test/java/lab/devlessons/unittest/SignupServiceTest.java
sources:
  - https://martinfowler.com/bliki/TestDouble.html
  - https://martinfowler.com/articles/mocksArentStubs.html
  - https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html
  - https://junit.org/junit5/docs/current/user-guide/
updated: 2026-11-25
---

선수 레슨: 의존 역전 원칙과 의존성 주입. 클래스가 협력 객체를 인터페이스로, 생성자에서 받는다는 것을 알면 돼요.
