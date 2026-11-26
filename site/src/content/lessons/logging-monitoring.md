---
title: "로그와 모니터링"
unit: 테스트와 운영
order: 1007
stage: 실무 시스템
summary: 로그를 어느 수준으로 무엇을 남기는지, 수천 줄 가운데 한 요청의 로그를 찾는 법, 서버가 느려졌는지 숫자로 보는 법, 알림을 거는 곳. 네 가지 질문에 스스로 답할 수 있게 주문 API의 로그와 지표로 배워요.
minutes: 20
page: logging-monitoring
verifiedBy:
  - verification/src/test/java/lab/devlessons/logging/LoggingMonitoringTest.java
sources:
  - https://www.slf4j.org/faq.html
  - https://docs.spring.io/spring-boot/reference/features/logging.html
  - https://docs.spring.io/spring-boot/reference/actuator/metrics.html
  - https://docs.micrometer.io/micrometer/reference/concepts/timers.html
  - https://sre.google/sre-book/monitoring-distributed-systems/
updated: 2026-11-25
---

선수 레슨: 스프링 MVC 요청 흐름, 가비지 컬렉션. 요청이 필터를 지나 컨트롤러에 닿는 순서와 GC의 멈춤을 알면 돼요.
