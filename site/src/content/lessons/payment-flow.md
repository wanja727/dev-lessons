---
title: 결제 시스템 흐름 (승인, 매입, 취소, 정산)
unit: Spring
order: 33
stage: 실무 시스템
summary: 카드 결제의 승인, 매입, 정산과 상태 전이를 배워요. 승인 응답 타임아웃을 실패로 처리하면 생기는 어긋남과 망 취소, 동시 부분 취소, PG 내역과 맞춰 보는 대사를 실제 DB와 가짜 PG로 재현해요.
minutes: 40
page: payment-flow
verifiedBy:
  - verification/src/test/java/lab/devlessons/payment/PaymentFlowTest.java
sources:
  - https://docs.tosspayments.com/resources/glossary/card-payment
  - https://docs.tosspayments.com/resources/glossary/transaction
  - https://docs.tosspayments.com/resources/glossary/settlement
  - https://docs.stripe.com/payments/place-a-hold-on-a-payment-method
  - https://docs.stripe.com/refunds
updated: 2026-11-17
---

선수 레슨: 멱등성, 분산 트랜잭션과 사가, 외부 API 타임아웃과 재시도.
