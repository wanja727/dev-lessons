---
title: "상속보다 조합"
unit: 객체지향 설계와 패턴
order: 306
stage: 기초
summary: 상속으로 재사용하면 왜 부모의 내부에 묶이는지, 부모의 메서드를 모두 물려받으면 무엇이 깨지는지, 조합으로 바꾸면 무엇이 달라지는지, 상속은 언제 쓰는지. 네 가지 질문에 스스로 답할 수 있게 장바구니의 상품 수 세기로 배워요.
minutes: 20
page: composition-over-inheritance
verifiedBy:
  - verification/src/test/java/lab/devlessons/composition/CompositionTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Stack.html
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.1.1.2
updated: 2026-11-25
---

선수 레슨: 리스코프 치환과 인터페이스 분리 원칙. 클래스 상속과 오버라이딩, `super`를 알면 돼요.
