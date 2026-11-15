---
title: 인덱스와 실행계획
unit: DB
summary: 인덱스가 조회를 빠르게 하는 원리와, 인덱스가 있어도 쓰이지 않는 경우(함수, 앞쪽 와일드카드, 낮은 선택도, 복합 인덱스 순서)를 PostgreSQL EXPLAIN 실험실에서 직접 확인해요.
minutes: 30
page: index-explain
verifiedBy:
  - verification/src/test/java/lab/devlessons/index/IndexPlanTest.java
sources:
  - https://www.postgresql.org/docs/16/using-explain.html
  - https://www.postgresql.org/docs/16/indexes-multicolumn.html
  - https://www.postgresql.org/docs/16/indexes-expressional.html
  - https://www.postgresql.org/docs/16/indexes-opclass.html
  - https://www.postgresql.org/docs/16/indexes-index-only-scans.html
updated: 2026-11-17
---

선수 레슨 없음.
