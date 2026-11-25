---
title: "빈의 생명주기와 스코프"
unit: 스프링
order: 703
stage: 기초
summary: 빈이 만들어지고 사라지는 순서, 초기화 코드를 @PostConstruct에 두는 이유, 싱글톤 빈에 상태를 두면 생기는 일, 요청마다 새 객체가 필요할 때의 스코프. 컨테이너를 열고 닫으며 순서를 찍고, 동시에 올린 숫자가 사라지는 것을 직접 봐요.
minutes: 20
page: bean-lifecycle-scope
verifiedBy:
  - verification/src/test/java/lab/devlessons/beanlifecycle/BeanLifecycleTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html
  - https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html
  - https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
updated: 2026-11-25
---

선수 레슨: 스프링 빈과 의존성 주입.
