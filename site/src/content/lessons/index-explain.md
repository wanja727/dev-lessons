---
title: 인덱스와 실행계획
unit: DB
order: 607
stage: DB와 운영
summary: 인덱스가 조회를 어떻게 빠르게 하는지, 인덱스가 있는데 왜 쓰이지 않는지, 복합 인덱스는 왜 열의 순서가 중요한지, 인덱스를 많이 만들면 무엇을 잃는지. 네 가지 질문에 스스로 답할 수 있게 주문 20만 건의 실행계획을 비교하며 배워요.
minutes: 25
page: index-explain
verifiedBy:
  - verification/src/test/java/lab/devlessons/index/IndexExplainTest.java
  - verification/src/test/java/lab/devlessons/index/IndexPlanTest.java
sources:
  - https://www.postgresql.org/docs/16/using-explain.html
  - https://www.postgresql.org/docs/16/indexes-multicolumn.html
  - https://www.postgresql.org/docs/16/indexes-expressional.html
  - https://www.postgresql.org/docs/16/indexes-opclass.html
  - https://www.postgresql.org/docs/16/indexes-index-only-scans.html
  - https://www.postgresql.org/docs/16/indexes-ordering.html
updated: 2026-11-25
---

선수 레슨: 조인, 이진 탐색 트리. 정렬된 데이터에서 절반씩 줄여 찾는다는 것을 알면 돼요.
