---
title: "집계와 서브쿼리"
unit: DB
order: 603
stage: 기초
summary: GROUP BY는 행을 어떻게 묶는지, WHERE와 HAVING은 무엇이 다른지, 서브쿼리는 어디에 쓰고 NOT IN은 왜 위험한지, 서브쿼리와 조인 가운데 무엇을 고르는지. 네 가지 질문에 스스로 답할 수 있게 회원별 결제 합계를 구하며 배워요.
minutes: 20
page: aggregate-subquery
verifiedBy:
  - verification/src/test/java/lab/devlessons/aggregate/AggregateSubqueryTest.java
sources:
  - https://www.postgresql.org/docs/16/functions-aggregate.html
  - https://www.postgresql.org/docs/16/queries-table-expressions.html
  - https://www.postgresql.org/docs/16/functions-subquery.html
  - https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html
updated: 2026-11-25
---

선수 레슨: 조인. INNER JOIN과 LEFT JOIN이 돌려주는 행을 알면 돼요.
