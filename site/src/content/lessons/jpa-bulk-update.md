---
title: "벌크 연산과 영속성 컨텍스트"
unit: JPA
order: 808
stage: 트랜잭션과 JPA
summary: 벌크 연산이 변경 감지와 무엇이 다른지, 벌크 연산 뒤 조회한 엔티티가 왜 옛 값인지, 둘을 어떻게 맞추는지, 벌크 연산에서 무엇이 빠지는지. 네 가지 질문에 스스로 답할 수 있게 상품 1,000개의 가격을 올리며 배워요.
minutes: 15
page: jpa-bulk-update
verifiedBy:
  - verification/src/test/java/lab/devlessons/bulkupdate/BulkUpdateTest.java
sources:
  - https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html
  - https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html#jpa.modifying-queries
updated: 2026-11-25
---

선수 레슨: 영속성 컨텍스트.
