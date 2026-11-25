---
title: "계층형 아키텍처"
unit: 시스템 설계와 아키텍처
order: 901
stage: 실무 시스템
summary: 컨트롤러, 서비스, 리포지토리로 나누는 이유, 의존이 한 방향이어야 하는 이유, 저장 방식을 바꿔도 서비스가 그대로인 이유, 계층 사이에 무엇을 넘기는지. 네 가지 질문에 스스로 답할 수 있게 회원 가입 API를 세 계층으로 나눠 봐요.
minutes: 20
page: layered-architecture
verifiedBy:
  - verification/src/test/java/lab/devlessons/layered/LayeredArchitectureTest.java
sources:
  - https://docs.spring.io/spring-framework/reference/data-access/dao.html
  - https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html
updated: 2026-11-25
---

선수 레슨: 스프링 MVC 요청 흐름, 의존 역전 원칙과 의존성 주입.
