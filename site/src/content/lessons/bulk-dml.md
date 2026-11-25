---
title: 대량 DML 안전하게 하기
unit: DB
order: 615
stage: DB와 운영
summary: 많은 행을 한 번에 고치면 무엇이 위험한지, 나눠서 하면 무엇이 달라지고 실패하면 어떻게 다시 하는지, 하기 전과 후에 무엇을 확인하는지, 잘못 고쳤으면 어떻게 되돌리는지. 네 가지 질문에 스스로 답할 수 있게 주문 5,000건의 상태를 바꾸는 운영 작업을 해 보며 배워요.
minutes: 20
page: bulk-dml
verifiedBy:
  - verification/src/test/java/lab/devlessons/dml/BulkDmlLessonTest.java
  - verification/src/test/java/lab/devlessons/dml/BulkDmlTest.java
sources:
  - https://www.postgresql.org/docs/16/sql-update.html
  - https://www.postgresql.org/docs/16/sql-createtableas.html
  - https://www.postgresql.org/docs/16/runtime-config-client.html
updated: 2026-11-25
---

선수 레슨: 트랜잭션 기초, 낙관적 락과 비관적 락. 커밋과 롤백, UPDATE가 행을 잠근다는 것을 알면 돼요.
