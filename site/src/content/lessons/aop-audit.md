---
title: "AOP와 감사 로그"
unit: Spring
order: 17
stage: 트랜잭션과 JPA
summary: 서비스 코드에 한 줄도 넣지 않고 "누가, 언제, 무엇을, 전후 값" 감사 로그를 남겨요. 실패한 시도의 기록이 롤백과 함께 사라지는 함정과 REQUIRES_NEW로 남기는 방법, 어드바이스 순서까지 배워요.
minutes: 30
page: aop-audit
verifiedBy:
  - verification/src/test/java/lab/devlessons/audit/AuditAspectTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/core/aop/ataspectj.html
  - https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html
  - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
updated: 2026-11-15
---

선수 레슨: @Transactional 동작 원리와 내부 호출(프록시).
