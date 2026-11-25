---
title: "분산 락"
unit: 시스템 설계와 아키텍처
order: 912
stage: 실무 시스템
summary: 서버가 여러 대일 때 Redis 락이 필요한 이유, 만료 시간과 주인 확인, 만료가 일보다 짧을 때의 한계와 펜싱 토큰. 네 가지 질문에 스스로 답할 수 있게 Redis로 서버 여러 대의 락을 재현해 봐요.
minutes: 20
page: distributed-lock
verifiedBy:
  - verification/src/test/java/lab/devlessons/distlock/DistributedLockTest.java
sources:
  - https://redis.io/docs/latest/commands/set/
  - https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/
  - https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html
  - https://redisson.org/docs/data-and-services/locks-and-synchronizers/
updated: 2026-11-25
---

선수 레슨: 동시 요청에서 재고 지키기.
