---
title: "멱등성과 중복 요청 방지"
unit: 시스템 설계와 아키텍처
order: 906
stage: 실무 시스템
summary: 같은 요청이 두 번 오는 이유, 먼저 조회하고 저장하면 막지 못하는 이유, 멱등 키와 유니크 제약으로 재시도에 답하는 법, 멱등 키만으로 부족한 곳. 네 가지 질문에 스스로 답할 수 있게 실제 PostgreSQL에 같은 결제를 동시에 보내 봐요.
minutes: 20
page: idempotency
verifiedBy:
  - verification/src/test/java/lab/devlessons/idempotency/IdempotencyTest.java
  - verification/src/test/java/lab/devlessons/idempotency/IdempotencyPracticeTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2
  - https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
  - https://docs.spring.io/spring-framework/reference/data-access/dao.html
  - https://www.postgresql.org/docs/16/ddl-constraints.html
updated: 2026-11-25
---

선수 레슨: @Transactional 동작 원리, REST API 설계.
