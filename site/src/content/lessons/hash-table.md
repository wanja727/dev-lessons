---
title: "해시 테이블"
unit: 자료구조와 알고리즘
order: 204
stage: 기초
summary: HashMap이 키로 값을 어떻게 한 번에 찾는지, 서로 다른 키가 같은 칸에 가면 어떻게 되는지, 언제 배열을 늘리는지, 돌면 왜 넣은 순서와 다른지. 네 가지 질문에 스스로 답할 수 있게 HashMap의 안쪽 배열을 열어 보며 배워요.
minutes: 25
page: hash-table
verifiedBy:
  - verification/src/test/java/lab/devlessons/hashtable/HashTableTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html
  - https://openjdk.org/jeps/180
updated: 2026-11-25
---

선수 레슨: 시간 복잡도, equals와 hashCode. `HashMap`이 `hashCode`로 찾을 곳을 좁힌다는 것을 알면 돼요.
