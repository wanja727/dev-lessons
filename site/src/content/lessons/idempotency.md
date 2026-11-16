---
title: 멱등성과 중복 요청 방지
unit: Spring
order: 18
stage: 트랜잭션과 JPA
summary: 결제가 두 번 되는 이유와 막는 법. 재시도와 더블 클릭을 재현해 먼저 조회하는 방식이 동시 요청에서 실패하는 걸 보고, 멱등 키와 유니크 제약으로 처음 결과를 돌려주는 설계를 배워요.
minutes: 30
page: idempotency
verifiedBy:
  - verification/src/test/java/lab/devlessons/idempotency/IdempotencyTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110.html
  - https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/
  - https://docs.stripe.com/api/idempotent_requests
updated: 2026-11-16
---

선수 레슨: @Transactional 동작 원리.
