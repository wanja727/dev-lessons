---
title: "프록시와 데코레이터 패턴"
unit: 객체지향 설계와 패턴
order: 312
stage: 기초
summary: 같은 인터페이스로 감싼다는 것, 프록시와 데코레이터의 차이, 여러 겹으로 감쌀 때 순서, 실행 중에 프록시를 만드는 법과 자기 호출의 함정. 네 가지 질문에 스스로 답할 수 있게 상품 가격 조회 서비스를 감싸며 배워요.
minutes: 22
page: proxy-decorator
verifiedBy:
  - verification/src/test/java/lab/devlessons/proxydecorator/ProxyDecoratorTest.java
sources:
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Proxy.html
  - https://docs.spring.io/spring-framework/reference/core/aop/proxying.html
updated: 2026-11-25
---

선수 레슨: 전략 패턴. 인터페이스로 받고 구현을 갈아 끼우는 것, 필드로 갖고 위임하는 것을 알면 돼요.
