---
title: "스프링 이벤트"
unit: 스프링
order: 713
stage: 실무 시스템
summary: 스프링 이벤트의 리스너가 언제 어느 스레드에서 도는지, 리스너가 실패하면 이벤트를 낸 쪽이 어떻게 되는지, 커밋 뒤에 도는 리스너에서 조심할 것. 회원 가입 이벤트로 DB에 남은 행을 확인해요.
minutes: 16
page: spring-events
verifiedBy:
  - verification/src/test/java/lab/devlessons/springevents/SpringEventsTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html
updated: 2026-11-26
---

선수 레슨: 트랜잭션 전파와 롤백 규칙, 옵저버 패턴과 이벤트.
