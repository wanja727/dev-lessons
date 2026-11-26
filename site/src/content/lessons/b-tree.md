---
title: "B-트리"
unit: 자료구조와 알고리즘
order: 213
stage: 기초
summary: 이진 탐색 트리를 디스크에 두면 왜 느린지, B-트리는 어떻게 디스크를 덜 읽는지, 키를 넣을 때 어떻게 균형을 지키는지, B+트리가 범위 조회에 왜 좋은지. 네 가지 질문에 스스로 답할 수 있게 PostgreSQL 인덱스가 읽는 페이지를 세어 보며 배워요.
minutes: 25
page: b-tree
verifiedBy:
  - verification/src/test/java/lab/devlessons/btree/BTreeTest.java
sources:
  - https://www.postgresql.org/docs/16/btree.html
  - https://www.postgresql.org/docs/16/storage-page-layout.html
  - https://www.postgresql.org/docs/16/pageinspect.html
  - https://www.postgresql.org/docs/16/sql-explain.html
updated: 2026-11-26
---

선수 레슨: 이진 탐색 트리. 균형이 깨지면 트리가 깊어진다는 것을 알면 돼요. 심화 레슨이에요.
