---
title: "결제 시스템 흐름"
unit: 시스템 설계와 아키텍처
order: 920
stage: 실무 시스템
summary: 카드 결제의 준비, 승인, 매입, 정산과 상태, 승인 응답이 타임아웃일 때의 망 취소, 부분 취소의 한도와 동시 요청, PG 내역과 맞추는 대사. 네 가지 질문에 스스로 답할 수 있게 가짜 PG로 결제를 흘려 봐요.
minutes: 25
page: payment-flow
verifiedBy:
  - verification/src/test/java/lab/devlessons/payment/PaymentFlowTest.java
sources:
  - https://docs.tosspayments.com/resources/glossary/card-payment
  - https://docs.tosspayments.com/resources/glossary/settlement
  - https://docs.stripe.com/payments/place-a-hold-on-a-payment-method
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html
updated: 2026-11-25
---

선수 레슨: 멱등성과 중복 요청 방지, 타임아웃, 재시도, 서킷 브레이커, 분산 트랜잭션과 사가.
