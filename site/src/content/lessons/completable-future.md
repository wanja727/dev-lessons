---
title: CompletableFuture
unit: 동시성·분산
summary: Future의 한계를 넘어 비동기 결과를 이어 붙이고(thenApply, thenCompose), 합치고(thenCombine, allOf), 실패와 타임아웃을 처리해요. 주문 화면 조합 시뮬레이터로 블로킹 없는 조합을 직접 확인해요.
minutes: 30
page: completable-future
verifiedBy:
  - verification/src/test/java/lab/devlessons/concurrency/CompletableFutureTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletionStage.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ForkJoinPool.html
updated: 2026-11-17
---

선수 레슨: Future와 Callable.
