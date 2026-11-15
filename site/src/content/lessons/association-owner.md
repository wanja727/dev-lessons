---
title: "연관관계 주인과 양방향 매핑"
unit: JPA
summary: 양방향 매핑에서 FK를 관리하는 건 한쪽뿐이에요. team.getMembers().add()만 했는데 왜 FK가 null로 저장되는지, mappedBy의 의미와 편의 메서드, 일대다 단방향이 UPDATE를 더 보내는 이유를 객체와 테이블을 나란히 놓고 확인해요.
minutes: 25
page: association-owner
verifiedBy:
  - verification/src/test/java/lab/devlessons/association/AssociationOwnerTest.java
sources:
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#associations-one-to-many
  - https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/OneToMany.html
updated: 2026-11-15
---

선수 레슨: 영속성 컨텍스트.
