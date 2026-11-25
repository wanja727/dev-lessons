---
title: 사용자 모드와 커널 모드
unit: 운영체제와 동시성
order: 411
stage: 동시성
summary: CPU는 왜 사용자 모드와 커널 모드를 나누는지, 시스템 콜은 무엇이고 자바 코드에서는 어디서 일어나는지, 입출력은 왜 비싸고 버퍼는 어떻게 빠르게 하는지. 세 가지 질문에 스스로 답할 수 있게 같은 파일을 한 바이트씩과 8KB씩 읽어 시간을 재 봐요.
minutes: 16
page: user-kernel-mode
verifiedBy:
  - verification/src/test/java/lab/devlessons/userkernel/UserKernelModeTest.java
sources:
  - https://man7.org/linux/man-pages/man2/syscalls.2.html
  - https://man7.org/linux/man-pages/man2/read.2.html
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/BufferedInputStream.html
  - https://logback.qos.ch/manual/appenders.html
updated: 2026-11-25
---

선수 레슨: 컨텍스트 스위칭. 커널이 스레드를 바꾸고 레지스터를 저장한다는 것을 알면 돼요.
