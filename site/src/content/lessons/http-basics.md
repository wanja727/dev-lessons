---
title: "HTTP 요청과 응답"
unit: 네트워크와 웹
order: 58
stage: 기초
summary: HTTP 요청과 응답이 어떻게 생겼는지, 메서드와 상태 코드가 무엇을 알려 주는지, 헤더가 하는 일, 연결 하나로 요청을 여러 번 보내는 법. 네 가지 질문에 스스로 답할 수 있게, 소켓으로 스프링 서버에 요청 글을 직접 보내 보며 배워요.
minutes: 20
page: http-basics
verifiedBy:
  - verification/src/test/java/lab/devlessons/httpbasics/HttpBasicsTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110.html
  - https://www.rfc-editor.org/rfc/rfc9112.html
  - https://www.rfc-editor.org/rfc/rfc9113.html
  - https://tomcat.apache.org/tomcat-10.1-doc/config/http.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
updated: 2026-11-25
---

선수 레슨: 브라우저에 주소를 입력하면. HTTP 요청이 TCP 연결 위로 가는 글이라는 것을 알면 돼요.
