---
title: Future와 CompletableFuture
unit: 운영체제와 동시성
order: 408
stage: 동시성
summary: 다른 스레드에 맡긴 일의 결과와 예외는 어떻게 받는지, CompletableFuture는 결과를 어떻게 잇고 합치는지, 실패와 시간 초과는 어떻게 다루는지. 네 가지 질문에 스스로 답할 수 있게 300ms 호출 셋을 기다리는 방법을 바꿔 가며 시간을 재 봐요.
minutes: 24
page: completable-future
verifiedBy:
  - verification/src/test/java/lab/devlessons/completablefuture/FutureAndCompletableFutureTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Future.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletionStage.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html
updated: 2026-11-25
---

선수 레슨: 스레드 풀, 람다와 함수형 인터페이스. 풀에 Callable을 submit하는 것과 Function, Supplier를 람다로 넘기는 것을 알면 돼요.
