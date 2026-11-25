---
title: 동기와 비동기, 블로킹과 논블로킹
unit: 운영체제와 동시성
order: 409
stage: 동시성
summary: 동기와 비동기는 무엇이 다른지, 블로킹과 논블로킹은 무엇이 다른지, 둘은 어떻게 조합되는지, 서버는 많은 요청을 어떤 방식으로 기다리는지. 네 가지 질문에 스스로 답할 수 있게 200ms 호출 100건을 스레드 10개와 콜백 하나로 기다려 봐요.
minutes: 20
page: sync-async-blocking
verifiedBy:
  - verification/src/test/java/lab/devlessons/syncasync/SyncAsyncBlockingTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/BlockingQueue.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/CompletableFuture.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/Selector.html
  - https://docs.spring.io/spring-framework/reference/web/webflux/new-framework.html
updated: 2026-11-25
---

선수 레슨: 스레드 풀. 스레드 풀과 BlockingQueue, 기다리는 스레드는 CPU를 쓰지 않는다는 것을 알면 돼요.
