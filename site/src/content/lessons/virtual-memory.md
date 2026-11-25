---
title: 가상 메모리
unit: 운영체제와 동시성
order: 410
stage: 동시성
summary: 프로세스마다 주소 공간이 따로인 이유, 페이징과 요구 페이징, 가상 크기와 실제 사용이 다른 이유, 물리 메모리가 모자랄 때 생기는 일. 세 가지 질문에 스스로 답할 수 있게 힙을 512MB로 준 JVM의 메모리 숫자를 읽어 봐요.
minutes: 16
page: virtual-memory
verifiedBy:
  - verification/src/test/java/lab/devlessons/virtualmemory/VirtualMemoryTest.java
sources:
  - https://man7.org/linux/man-pages/man5/proc.5.html
  - https://docs.oracle.com/en/java/javase/21/docs/specs/man/java.html
  - https://docs.kernel.org/admin-guide/mm/concepts.html
  - https://man7.org/linux/man-pages/man1/top.1.html
updated: 2026-11-25
---

선수 레슨: 프로세스와 스레드, JVM 메모리 구조. 프로세스끼리 메모리를 공유하지 않는다는 것과 -Xmx를 알면 돼요.
