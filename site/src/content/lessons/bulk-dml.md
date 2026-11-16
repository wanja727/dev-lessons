---
title: 대량 DML 안전하게 하기
unit: DB
order: 22
stage: DB와 운영
summary: 운영 DB에서 수천~수십만 건을 고칠 때의 절차. 백업 테이블, 전후 건수 비교, 분할 커밋, 재실행 가능한 조건, 원복 스크립트를 시뮬레이터와 테스트로 확인하고 작업 SQL 템플릿까지 가져가요.
minutes: 30
page: bulk-dml
verifiedBy:
  - verification/src/test/java/lab/devlessons/dml/BulkDmlTest.java
sources:
  - https://www.postgresql.org/docs/16/sql-update.html
  - https://www.postgresql.org/docs/16/sql-createtableas.html
  - https://www.postgresql.org/docs/16/runtime-config-client.html
updated: 2026-11-16
---

선수 레슨: 격리 수준과 이상 현상.
