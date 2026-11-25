---
title: "OSIV"
unit: JPA
order: 16
stage: 트랜잭션과 JPA
summary: OSIV가 무엇을 요청 끝까지 열어 두는지, 켜면 컨트롤러에서 무엇이 되는지, 켜 두면 무엇을 잃는지, 컨트롤러에서 바꾼 엔티티는 어떻게 되는지, 끄면 지연 로딩을 어디서 끝내는지. 다섯 가지 질문에 스스로 답할 수 있게 같은 컨트롤러를 켠 앱과 끈 앱에서 불러 보며 배워요.
minutes: 20
page: osiv
verifiedBy:
  - verification/src/test/java/lab/devlessons/osiv/OsivOffTest.java
  - verification/src/test/java/lab/devlessons/osiv/OsivOnTest.java
sources:
  - https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view
  - https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/orm/jpa/support/OpenEntityManagerInViewInterceptor.html
updated: 2026-11-25
---

선수 레슨: 프록시와 지연 로딩, @Transactional 동작 원리, DB 커넥션과 커넥션 풀.
