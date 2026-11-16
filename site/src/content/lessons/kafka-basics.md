---
title: 메시지 큐 기초 (Kafka)
unit: 동시성·분산
order: 32
stage: 실무 시스템
summary: Kafka의 토픽, 파티션, 키와 순서, 컨슈머 그룹을 실제 브로커로 확인해요. 처리와 오프셋 커밋의 순서에 따라 메시지가 중복되거나 사라지는 것을 재현하고, 멱등 컨슈머로 최소 한 번 전달을 안전하게 만들어요.
minutes: 35
page: kafka-basics
verifiedBy:
  - verification/src/test/java/lab/devlessons/kafka/KafkaBasicsTest.java
sources:
  - https://kafka.apache.org/documentation/#intro_concepts_and_terms
  - https://kafka.apache.org/documentation/#semantics
  - https://java.testcontainers.org/modules/kafka/
updated: 2026-11-17
---

선수 레슨: 트랜잭셔널 아웃박스.
