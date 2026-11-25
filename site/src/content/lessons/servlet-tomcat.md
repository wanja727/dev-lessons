---
title: "서블릿과 톰캣"
unit: 스프링
order: 701
stage: 기초
summary: 서블릿 컨테이너가 하는 일, 요청 하나를 맡는 스레드, 스레드가 모두 바쁠 때 새 요청이 기다리는 것, 서블릿 객체가 하나뿐이라는 것. 네 가지 질문에 스스로 답할 수 있게 내장 톰캣을 띄워 스레드 이름과 응답 시간을 재 봐요.
minutes: 18
page: servlet-tomcat
verifiedBy:
  - verification/src/test/java/lab/devlessons/servlettomcat/ServletTomcatTest.java
sources:
  - https://jakarta.ee/specifications/servlet/6.0/jakarta-servlet-spec-6.0
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
updated: 2026-11-25
---

선수 레슨: HTTP 요청과 응답, 스레드 풀. 스프링 MVC는 몰라도 돼요.
