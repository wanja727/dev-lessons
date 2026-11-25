---
title: "선착순 이벤트 설계"
unit: 시스템 설계와 아키텍처
order: 914
stage: 실무 시스템
summary: 몰리는 요청을 DB 앞에서 받는 방법, 수량과 중복 참여를 Redis 한 번의 실행으로 판정하는 이유, 당첨자를 대기열로 넘겨 저장하는 구조, 앞에서 막아 둘 것들. 네 가지 질문에 스스로 답할 수 있게 쿠폰 100장에 요청 600건을 보내 봐요.
minutes: 20
page: first-come-event
verifiedBy:
  - verification/src/test/java/lab/devlessons/firstcome/FirstComeEventTest.java
sources:
  - https://redis.io/docs/latest/develop/interact/programmability/eval-intro/
  - https://redis.io/docs/latest/develop/data-types/lists/
updated: 2026-11-25
---

선수 레슨: 동시 요청에서 재고 지키기, 메시지 큐, 요청 수 제한.
