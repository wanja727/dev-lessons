---
title: "스프링 MVC 요청 흐름"
unit: 스프링
order: 706
stage: 기초
summary: 요청이 컨트롤러 메서드를 찾아가는 길, 메서드의 인자를 채우는 일, 반환한 객체가 응답 본문이 되는 길. 단계마다 일부러 실패하는 요청을 보내 어느 단계에서 어떤 상태 코드가 나오는지 봐요.
minutes: 18
page: spring-mvc-flow
verifiedBy:
  - verification/src/test/java/lab/devlessons/mvcflow/MvcFlowTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet/special-bean-types.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/message-converters.html
updated: 2026-11-25
---

선수 레슨: 스프링 빈과 의존성 주입, 서블릿과 톰캣.
