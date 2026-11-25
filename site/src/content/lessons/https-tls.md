---
title: "HTTPS와 TLS"
unit: 네트워크와 웹
order: 60
stage: 기초
summary: HTTP의 무엇이 위험하고 HTTPS가 무엇을 지키는지, TLS가 내용을 암호화하는 방법, 인증서가 증명하는 것, TLS 연결을 맺는 순서. 네 가지 질문에 스스로 답할 수 있게, 오가는 바이트를 엿보고 직접 만든 인증서로 연결해 보며 배워요.
minutes: 22
page: https-tls
verifiedBy:
  - verification/src/test/java/lab/devlessons/httpstls/HttpsTlsTest.java
sources:
  - https://www.rfc-editor.org/rfc/rfc9110.html#section-4.2.2
  - https://www.rfc-editor.org/rfc/rfc8446.html
  - https://www.rfc-editor.org/rfc/rfc5280.html
  - https://www.rfc-editor.org/rfc/rfc9525.html
  - https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html
  - https://docs.spring.io/spring-boot/reference/features/ssl.html
updated: 2026-11-25
---

선수 레슨: TCP와 UDP, HTTP 요청과 응답. HTTP 요청이 TCP 연결 위로 가는 글이라는 것을 알면 돼요.
