---
title: "equals와 hashCode"
unit: Java
order: 34
stage: 기초
summary: 값이 같은 두 객체를 같다고 판정하게 하는 법, equals만 재정의하면 HashSet이 못 찾는 까닭, hashCode의 규약, 키로 넣은 뒤 필드를 바꾸면 생기는 일. 다섯 가지 질문에 스스로 답할 수 있게 회원 클래스 하나로 배워요.
minutes: 20
page: equals-hashcode
verifiedBy:
  - verification/src/test/java/lab/devlessons/equalshashcode/EqualsHashCodeTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Object.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Collection.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Map.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Record.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html
updated: 2026-11-23
---

선수 레슨: 값과 참조(아직 없는 레슨). `==`가 같은 객체인지 본다는 것과 `ArrayList`, `HashSet`, `HashMap`에 넣고 꺼내는 방법만 알면 돼요.
