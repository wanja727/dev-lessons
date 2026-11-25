---
title: "리스코프 치환과 인터페이스 분리 원칙"
unit: 객체지향 설계와 패턴
order: 304
stage: 기초
summary: 자식을 부모 자리에 넣어도 맞게 동작해야 한다는 원칙, 그것을 깨는 상속의 모양, 쓰는 쪽에 맞춰 인터페이스를 나누는 원칙, 나눴을 때 좋아지는 것. 네 가지 질문에 스스로 답할 수 있게 부분 환불을 못 하는 상품권 결제를 고쳐 가며 배워요.
minutes: 20
page: lsp-isp
verifiedBy:
  - verification/src/test/java/lab/devlessons/lspisp/LspIspTest.java
sources:
  - https://www.cs.cmu.edu/~wing/publications/LiskovWing94.pdf
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/List.html
  - https://docs.spring.io/spring-data/commons/reference/repositories/definition.html
updated: 2026-11-25
---

선수 레슨: 단일 책임과 개방 폐쇄 원칙. 인터페이스 구현과 오버라이딩, 다형성으로 부르는 쪽을 고치지 않는 것을 알면 돼요.
