---
title: "페이지네이션"
unit: DB
order: 608
stage: DB와 운영
summary: OFFSET 페이지네이션은 어떻게 동작하는지, 뒤 페이지는 왜 느려지는지, 페이지를 넘기는 사이에 글이 추가되면 어떻게 되는지, 커서 페이지네이션은 무엇을 바꾸는지. 네 가지 질문에 스스로 답할 수 있게 게시글 100만 건을 20개씩 읽으며 배워요.
minutes: 20
page: pagination
verifiedBy:
  - verification/src/test/java/lab/devlessons/pagination/PaginationTest.java
sources:
  - https://www.postgresql.org/docs/16/queries-limit.html
  - https://www.postgresql.org/docs/16/functions-comparisons.html
  - https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html
  - https://docs.spring.io/spring-data/commons/reference/repositories/scrolling.html
updated: 2026-11-25
---

선수 레슨: 인덱스와 실행계획. 복합 인덱스의 열 순서와 실행계획 읽는 법을 알면 돼요.
