---
title: "키와 제약 조건"
unit: DB
order: 601
stage: 기초
summary: 기본 키는 무엇이고 왜 의미 없는 번호를 쓰는지, 외래 키는 무엇을 막는지, NOT NULL과 UNIQUE와 CHECK는 각각 무엇을 지키는지, 앱에서 확인하는데도 왜 DB 제약이 필요한지. 네 가지 질문에 스스로 답할 수 있게 회원과 주문 두 테이블로 배워요.
minutes: 20
page: keys-constraints
verifiedBy:
  - verification/src/test/java/lab/devlessons/keys/KeysConstraintsTest.java
sources:
  - https://www.postgresql.org/docs/16/ddl-constraints.html
  - https://www.postgresql.org/docs/16/errcodes-appendix.html
  - https://docs.spring.io/spring-framework/reference/data-access/dao.html
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
updated: 2026-11-25
---

선수 레슨 없음. SQL의 CREATE TABLE, INSERT, SELECT를 한 번 써 봤으면 돼요.
