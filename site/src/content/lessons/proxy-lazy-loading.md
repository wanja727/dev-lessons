---
title: "프록시와 지연 로딩"
unit: JPA
summary: 연관 엔티티 자리에 들어가는 가짜 객체, 프록시가 언제 SELECT를 보내는지 버튼으로 직접 호출해 보며 확인해요. find와 getReference의 차이, LazyInitializationException이 나는 이유와 해결법까지 배워요.
minutes: 25
page: proxy-lazy-loading
verifiedBy:
  - verification/src/test/java/lab/devlessons/lazy/ProxyLazyLoadingTest.java
sources:
  - https://docs.hibernate.org/orm/6.6/userguide/html_single/#fetching
  - https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/EntityManager.html
updated: 2026-11-15
---

선수 레슨: 영속성 컨텍스트. 다음 레슨: N+1 문제.
