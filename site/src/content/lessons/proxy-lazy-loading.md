---
title: "프록시와 지연 로딩"
unit: JPA
order: 11
stage: 트랜잭션과 JPA
summary: 지연 로딩이면 연관 필드에 무엇이 들어 있는지, 프록시는 언제 SELECT를 보내는지, LazyInitializationException은 왜 나는지, 어떻게 막는지. 네 가지 질문에 스스로 답할 수 있게 선수의 팀 필드를 한 줄씩 건드리며 배워요.
minutes: 20
page: proxy-lazy-loading
verifiedBy:
  - verification/src/test/java/lab/devlessons/lazy/ProxyLazyLoadingTest.java
sources:
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#fetching
  - https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/ManyToOne.html
  - https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.entity-graph
updated: 2026-11-25
---

선수 레슨: 영속성 컨텍스트. `@ManyToOne`으로 연관을 매핑해 본 적이 있으면(연관관계 매핑) 더 쉬워요.
