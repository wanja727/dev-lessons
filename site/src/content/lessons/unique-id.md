---
title: "유일한 ID 만들기"
unit: 시스템 설계와 아키텍처
order: 917
stage: 실무 시스템
summary: DB 자동 증가 번호의 장점과 한계, 무작위 UUID가 인덱스를 키우는 이유, 시간 순서 ID(UUIDv7, 스노플레이크), 상황마다 고르는 기준. 네 가지 질문에 스스로 답할 수 있게 세 방식을 만들어 비교해 봐요.
minutes: 20
page: unique-id
verifiedBy:
  - verification/src/test/java/lab/devlessons/uniqueid/UniqueIdTest.java
sources:
  - https://www.postgresql.org/docs/16/sql-createtable.html
  - https://www.postgresql.org/docs/16/functions-sequence.html
  - https://www.rfc-editor.org/rfc/rfc9562
  - https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html
updated: 2026-11-25
---

선수 레슨: 서버를 늘리는 방법.
