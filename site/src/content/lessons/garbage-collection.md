---
title: "가비지 컬렉션"
unit: 자바 언어
order: 115
stage: 기초
summary: 가비지 컬렉터가 어떤 객체를 치우는지, 힙을 왜 세대로 나눠 치우는지, 치우는 동안 애플리케이션은 어떻게 되는지, 가비지 컬렉터가 있는데 왜 메모리가 새는지. 네 가지 질문에 스스로 답할 수 있게 서로를 가리키는 주문과 끝없이 커지는 캐시로 배워요.
minutes: 22
page: garbage-collection
verifiedBy:
  - verification/src/test/java/lab/devlessons/gc/GarbageCollectionTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/gctuning/available-collectors.html
  - https://docs.oracle.com/en/java/javase/21/gctuning/ergonomics1.html
  - https://docs.oracle.com/en/java/javase/21/gctuning/garbage-collector-implementation1.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ref/package-summary.html
updated: 2026-11-25
---

선수 레슨: JVM 메모리 구조. 객체는 힙에 놓이고 지역 변수에는 참조만 있다는 것을 알면 돼요.
