---
title: "숫자 타입의 한계"
unit: 자바 언어
order: 40
stage: 기초
summary: 정수 계산이 범위를 넘으면 무슨 일이 생기는지, 0.1 + 0.2가 왜 0.3이 아닌지, 돈은 무엇으로 계산하는지, BigDecimal 둘을 어떻게 비교하는지. 네 가지 질문에 스스로 답할 수 있게 쇼핑몰의 금액 계산으로 배워요.
minutes: 20
page: number-limits
verifiedBy:
  - verification/src/test/java/lab/devlessons/numberlimits/NumberLimitsTest.java
  - verification/src/test/java/lab/devlessons/numberlimits/NumberLimitsJpaTest.java
sources:
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.2
  - https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3
  - https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html
  - https://jakarta.ee/specifications/persistence/3.1/apidocs/jakarta.persistence/jakarta/persistence/Column.html
updated: 2026-11-25
---

선수 레슨: 래퍼 클래스와 오토박싱. `int`, `long`, `double`로 계산해 본 적이 있으면 돼요.
