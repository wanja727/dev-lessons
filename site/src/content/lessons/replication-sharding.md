---
title: "DB 복제와 샤딩"
unit: 시스템 설계와 아키텍처
order: 915
stage: 실무 시스템
summary: 읽기를 나누는 복제, 복제 지연이 만드는 방금 쓴 것이 안 보이는 문제, 샤드 키로 나눠 담는 샤딩과 그 대가, 샤드를 늘릴 때 옮겨 가는 양. 네 가지 질문에 스스로 답할 수 있게 PostgreSQL 두 대로 직접 해 봐요.
minutes: 25
page: replication-sharding
verifiedBy:
  - verification/src/test/java/lab/devlessons/replication/ReplicationShardingTest.java
sources:
  - https://www.postgresql.org/docs/16/logical-replication.html
  - https://www.postgresql.org/docs/16/high-availability.html
  - https://www.postgresql.org/docs/16/warm-standby.html
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html
updated: 2026-11-26
---

선수 레슨: 격리 수준과 이상 현상, 서버를 늘리는 방법.
