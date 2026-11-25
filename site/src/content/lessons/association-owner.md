---
title: "연관관계 매핑"
unit: JPA
order: 13
stage: 트랜잭션과 JPA
summary: 객체의 참조와 테이블의 외래 키가 어떻게 다른지, 양방향일 때 DB에 반영되는 쪽은 어디인지, 주인만 바꾸면 무엇이 어긋나는지, 양방향은 언제 쓰고 일대다 단방향은 왜 피하는지. 네 가지 질문에 스스로 답할 수 있게 멤버를 팀에 넣고 빼며 배워요.
minutes: 20
page: association-owner
verifiedBy:
  - verification/src/test/java/lab/devlessons/association/AssociationOwnerTest.java
sources:
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#associations
updated: 2026-11-25
---

선수 레슨: 영속성 컨텍스트, 조인. 외래 키가 다른 테이블의 행을 가리킨다는 것을 알면 돼요.
