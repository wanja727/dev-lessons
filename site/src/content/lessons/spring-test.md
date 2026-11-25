---
title: "스프링 테스트"
unit: 테스트와 운영
order: 903
stage: 실무 시스템
summary: '@SpringBootTest가 띄우는 것, @WebMvcTest와 @DataJpaTest 같은 슬라이스 테스트, 테스트에 붙인 @Transactional이 운영과 다르게 만드는 것, 테스트가 많아지면 느려지는 이유. 네 가지 질문에 스스로 답할 수 있게 회원 API로 배워요.'
minutes: 20
page: spring-test
verifiedBy:
  - verification/src/test/java/lab/devlessons/springtest/FullContextTest.java
  - verification/src/test/java/lab/devlessons/springtest/WebSliceTest.java
  - verification/src/test/java/lab/devlessons/springtest/DataJpaSliceTest.java
  - verification/src/test/java/lab/devlessons/springtest/LazyLoadingTest.java
  - verification/src/test/java/lab/devlessons/springtest/ContextCacheTest.java
sources:
  - https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
  - https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html
  - https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/tx.html
  - https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.4-Release-Notes
updated: 2026-11-25
---

선수 레슨: @Transactional 동작 원리, 단위 테스트와 테스트 더블. 트랜잭션이 프록시로 열리고 닫힌다는 것과 목과 스텁을 알면 돼요.
