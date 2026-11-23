---
title: "단일 책임과 개방 폐쇄 원칙"
unit: 객체지향 설계와 패턴
order: 35
stage: 기초
summary: 클래스가 바뀌는 이유를 세는 법, 책임을 나누는 기준, if와 else if로 나눈 코드가 개방 폐쇄 원칙을 어기는 까닭, 인터페이스로 빼는 방법과 그 한계. 다섯 가지 질문에 스스로 답할 수 있게 주문 결제 코드 하나를 두 번 고치며 배워요.
minutes: 20
page: srp-ocp
verifiedBy:
  - verification/src/test/java/lab/devlessons/srpocp/SrpOcpChangeTest.java
  - verification/src/test/java/lab/devlessons/srpocp/a/CodeATest.java
  - verification/src/test/java/lab/devlessons/srpocp/b/CodeBTest.java
  - verification/src/test/java/lab/devlessons/srpocp/bforgot/CodeBForgotTest.java
  - verification/src/test/java/lab/devlessons/srpocp/bsilver/CodeBSilverTest.java
  - verification/src/test/java/lab/devlessons/srpocp/c/CodeCTest.java
  - verification/src/test/java/lab/devlessons/srpocp/csilver/CodeCSilverTest.java
sources:
  - https://blog.cleancoder.com/uncle-bob/2014/05/08/SingleReponsibilityPrinciple.html
  - https://blog.cleancoder.com/uncle-bob/2014/05/12/TheOpenClosedPrinciple.html
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html
updated: 2026-11-23
---

선수 레슨: 결합도와 응집도(아직 없는 레슨). 인터페이스를 선언하고 클래스가 구현하는 문법과 `enum`, `List`를 for 문으로 도는 방법만 알면 돼요.
