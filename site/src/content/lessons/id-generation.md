---
title: "식별자 생성 전략"
unit: JPA
order: 809
stage: 트랜잭션과 JPA
summary: IDENTITY와 SEQUENCE가 id를 언제 얻는지, allocationSize가 무엇을 아끼는지, 한꺼번에 많이 넣을 때 왜 차이가 나는지, 롤백하면 번호가 돌아오는지. 네 가지 질문에 스스로 답할 수 있게 주문 로그 1,000건을 두 전략으로 저장하며 배워요.
minutes: 15
page: id-generation
verifiedBy:
  - verification/src/test/java/lab/devlessons/idgen/IdGenerationTest.java
  - verification/src/test/java/lab/devlessons/idgen/IdGenerationBatchTest.java
sources:
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#identifiers-generators
  - https://www.postgresql.org/docs/16/functions-sequence.html
updated: 2026-11-25
---

선수 레슨: 영속성 컨텍스트.
