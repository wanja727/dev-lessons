---
title: "테이블 관계 설계"
unit: DB
order: 617
stage: 기초
summary: 요구사항에서 테이블과 관계를 어떻게 찾는지, ERD의 선 끝 기호는 무엇을 뜻하는지, 1:N과 1:1은 외래 키를 어디에 어떻게 두는지, N:M은 왜 외래 키 하나로 담을 수 없는지. 다섯 가지 질문에 스스로 답할 수 있게 스터디 서비스의 회원, 프로필, 글, 스터디로 배워요.
minutes: 20
page: table-relationships
verifiedBy:
  - verification/src/test/java/lab/devlessons/relationship/TableRelationshipsTest.java
sources:
  - https://mermaid.js.org/syntax/entityRelationshipDiagram.html
  - https://www.postgresql.org/docs/16/ddl-constraints.html
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/
updated: 2026-11-27
---

선수 레슨: 키와 제약 조건, 조인. 기본 키와 외래 키, 두 테이블을 조인하는 SQL을 알면 돼요.
