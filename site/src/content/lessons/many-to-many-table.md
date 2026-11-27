---
title: "다대다와 매핑 테이블"
unit: DB
order: 618
stage: DB와 운영
summary: 매핑 테이블에 같은 쌍이 두 번 들어가면 무슨 일이 생기는지, 기본 키를 복합 키로 둘지 따로 id를 둘지, 양쪽 방향 조회에 인덱스를 어떻게 두는지, 가입일과 역할은 어디에 두는지, 부모를 지울 때 매핑 행은 어떻게 하는지. 다섯 가지 질문에 스스로 답할 수 있게 회원과 스터디의 매핑 테이블로 배워요.
minutes: 25
page: many-to-many-table
verifiedBy:
  - verification/src/test/java/lab/devlessons/manytomany/ManyToManyTableTest.java
sources:
  - https://www.postgresql.org/docs/16/ddl-constraints.html
  - https://www.postgresql.org/docs/16/indexes-multicolumn.html
  - https://www.postgresql.org/docs/16/indexes-partial.html
  - https://www.postgresql.org/docs/16/sql-insert.html
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
updated: 2026-11-27
---

선수 레슨: 테이블 관계 설계, 인덱스와 실행계획. N:M을 매핑 테이블로 푸는 이유와 복합 인덱스의 열 순서를 알면 돼요.
