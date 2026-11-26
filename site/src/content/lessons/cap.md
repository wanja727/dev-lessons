---
title: "CAP 정리와 결과적 일관성"
unit: 시스템 설계와 아키텍처
order: 916
stage: 실무 시스템
summary: 네트워크가 갈라졌을 때 일관성과 가용성 가운데 하나를 고르는 이유, 일관성을 고르면 생기는 거절, 가용성을 고르면 생기는 엇갈림과 결과적 일관성, 정족수. 네 가지 질문에 스스로 답할 수 있게 복제본 세 개짜리 모형에 분할을 일으켜 봐요.
minutes: 20
page: cap
verifiedBy:
  - verification/src/test/java/lab/devlessons/cap/CapTest.java
sources:
  - https://www.infoq.com/articles/cap-twelve-years-later-how-the-rules-have-changed/
  - https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf
  - https://cassandra.apache.org/doc/latest/cassandra/architecture/dynamo.html
updated: 2026-11-26
---

선수 레슨: DB 복제와 샤딩.
