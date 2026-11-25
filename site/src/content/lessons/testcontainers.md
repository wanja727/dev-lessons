---
title: "통합 테스트와 Testcontainers"
unit: 테스트와 운영
order: 904
stage: 실무 시스템
summary: H2 대신 진짜 DB로 테스트하는 이유, Testcontainers가 테스트에 DB를 붙이는 방법, 컨테이너가 뜨는 횟수, 테스트끼리 데이터가 섞이지 않게 하는 법. 네 가지 질문에 스스로 답할 수 있게 상품 재고 쿼리로 배워요.
minutes: 20
page: testcontainers
verifiedBy:
  - verification/src/test/java/lab/devlessons/testcontainers/TestcontainersTest.java
sources:
  - https://java.testcontainers.org/test_framework_integration/junit_5/
  - https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/
  - https://docs.spring.io/spring-boot/reference/testing/testcontainers.html
  - https://www.h2database.com/html/features.html
updated: 2026-11-25
---

선수 레슨: 스프링 테스트. `@DataJpaTest`와 테스트의 트랜잭션 롤백을 알면 돼요.
