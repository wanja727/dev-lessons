---
title: "느린 쿼리 고치기"
unit: DB
order: 614
stage: DB와 운영
summary: 느린 쿼리는 어떻게 찾는지, 실행계획에서 원인을 어떻게 읽는지, 고친 뒤에는 어떻게 다시 재는지, 흔한 원인은 무엇이고 어떻게 고치는지. 네 가지 질문에 스스로 답할 수 있게 주문 100만 건에서 느린 쿼리를 찾고 고치는 과정을 한 바퀴 돌며 배워요.
minutes: 20
page: slow-query
verifiedBy:
  - verification/src/test/java/lab/devlessons/slowquery/SlowQueryTest.java
sources:
  - https://www.postgresql.org/docs/16/using-explain.html
  - https://www.postgresql.org/docs/16/planner-stats.html
  - https://www.postgresql.org/docs/16/runtime-config-logging.html
  - https://www.postgresql.org/docs/16/pgstatstatements.html
updated: 2026-11-25
---

선수 레슨: 인덱스와 실행계획, 집계와 서브쿼리. EXPLAIN ANALYZE를 읽을 줄 알면 돼요.
