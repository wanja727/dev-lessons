---
title: "부하 테스트"
unit: 테스트와 운영
order: 909
stage: 실무 시스템
summary: 부하 테스트로 알아내는 것, 사용자를 늘릴 때 처리량과 응답 시간이 바뀌는 모양, 병목을 찾는 법, 믿을 수 있게 재는 법. 네 가지 질문에 스스로 답할 수 있게 스레드를 줄인 상품 조회 서버로 배워요.
minutes: 20
page: load-testing
verifiedBy:
  - verification/src/test/java/lab/devlessons/loadtest/LoadTestingTest.java
sources:
  - https://en.wikipedia.org/wiki/Little%27s_law
  - https://docs.spring.io/spring-boot/appendix/application-properties/index.html
  - https://grafana.com/docs/k6/latest/using-k6/thresholds/
  - https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/open-vs-closed/
updated: 2026-11-26
---

선수 레슨: 로그와 모니터링, 스레드 풀. 응답 시간을 백분위로 보는 것과 스레드 풀이 차면 일이 기다린다는 것을 알면 돼요.
