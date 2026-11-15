---
title: "OSIV (Open Session In View)"
unit: JPA
summary: 스프링 부트는 기본으로 영속성 컨텍스트를 요청 끝까지 열어 둬요. 덕분에 컨트롤러에서 지연 로딩이 되지만, DB 커넥션도 요청 끝까지 붙잡아요. 켜고 끈 차이를 요청 타임라인과 커넥션 풀 계산으로 확인하고, 왜 끄라고 하는지 배워요.
minutes: 25
page: osiv
verifiedBy:
  - verification/src/test/java/lab/devlessons/osiv/OsivOffTest.java
  - verification/src/test/java/lab/devlessons/osiv/OsivOnTest.java
sources:
  - https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/support/OpenEntityManagerInViewInterceptor.html
updated: 2026-11-15
---

선수 레슨: @Transactional 동작 원리, 프록시와 지연 로딩.
