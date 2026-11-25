---
title: "예외 처리와 검증"
unit: 스프링
order: 708
stage: 기초
summary: 컨트롤러에서 난 예외가 응답이 되는 길, 예외를 원하는 응답으로 바꾸는 @RestControllerAdvice, 잘못된 입력을 막는 검증 어노테이션, 에러 응답을 한 형식으로 맞추는 법. 핸들러를 하나씩 더해 가며 응답이 바뀌는 것을 봐요.
minutes: 20
page: exception-validation
verifiedBy:
  - verification/src/test/java/lab/devlessons/exceptionvalidation/ExceptionValidationTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html
  - https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
  - https://docs.spring.io/spring-boot/reference/io/validation.html
  - https://www.rfc-editor.org/rfc/rfc9457
updated: 2026-11-25
---

선수 레슨: 스프링 MVC 요청 흐름, REST API 설계.
