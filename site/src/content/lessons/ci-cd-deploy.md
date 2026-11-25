---
title: "CI/CD와 무중단 배포"
unit: 테스트와 운영
order: 906
stage: 실무 시스템
summary: CI가 자동으로 확인하는 것, CD가 자동으로 하는 것, 서버를 멈추지 않고 새 버전으로 바꾸는 방법, 서버를 끌 때 처리 중이던 요청, 두 버전이 함께 도는 동안 지킬 것. 다섯 가지 질문에 스스로 답할 수 있게 주문 서버와 이 저장소의 워크플로로 배워요.
minutes: 25
page: ci-cd-deploy
verifiedBy:
  - verification/src/test/java/lab/devlessons/cicd/GracefulShutdownTest.java
sources:
  - https://martinfowler.com/articles/continuousIntegration.html
  - https://docs.github.com/en/actions/writing-workflows/workflow-syntax-for-github-actions
  - https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
  - https://martinfowler.com/bliki/BlueGreenDeployment.html
  - https://docs.docker.com/reference/cli/docker/container/stop/
updated: 2026-11-25
---

선수 레슨: Git 브랜치와 병합, 도커와 컨테이너, 통합 테스트와 Testcontainers. PR, 이미지, 자동으로 도는 테스트를 알면 돼요.
