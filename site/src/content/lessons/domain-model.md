---
title: "도메인 모델과 트랜잭션 스크립트"
unit: 시스템 설계와 아키텍처
order: 902
stage: 실무 시스템
summary: 업무 로직을 서비스에 쓰는 트랜잭션 스크립트와 엔티티에 두는 도메인 모델, 규칙이 엔티티에 있을 때 달라지는 것, 애그리거트의 경계, 고르는 기준. 네 가지 질문에 스스로 답할 수 있게 같은 주문 취소를 두 방식으로 짜 봐요.
minutes: 20
page: domain-model
verifiedBy:
  - verification/src/test/java/lab/devlessons/domainmodel/DomainModelTest.java
sources:
  - https://martinfowler.com/eaaCatalog/transactionScript.html
  - https://martinfowler.com/eaaCatalog/domainModel.html
  - https://martinfowler.com/bliki/DDD_Aggregate.html
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1
updated: 2026-11-25
---

선수 레슨: 계층형 아키텍처, 영속성 컨텍스트.
