---
title: "도커와 컨테이너"
unit: 테스트와 운영
order: 1005
stage: 실무 시스템
summary: 컨테이너가 가상 머신과 다른 점, 이미지와 컨테이너의 차이, Dockerfile의 순서가 빌드 시간을 바꾸는 이유, 컨테이너에서 자바 앱의 메모리가 정해지는 방식. 네 가지 질문에 스스로 답할 수 있게 주문 서버를 컨테이너로 띄우며 배워요.
minutes: 20
page: docker-container
verifiedBy:
  - verification/src/test/java/lab/devlessons/docker/DockerContainerTest.java
sources:
  - https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/
  - https://docs.docker.com/build/cache/
  - https://docs.docker.com/engine/containers/resource_constraints/
  - https://bugs.openjdk.org/browse/JDK-8146115
updated: 2026-11-25
---

선수 레슨: 프로세스와 스레드, JVM 메모리 구조. 프로세스가 무엇인지와 JVM의 힙을 알면 돼요.
