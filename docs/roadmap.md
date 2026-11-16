# 로드맵

첫 목표는 백엔드 면접에서 자주 나오는 JPA·Spring·DB·Java 동시성 개념. 실제 면접에서 답하지 못한 주제를 가장 먼저 만든다. 화살표는 선수 관계(먼저 배우면 좋은 레슨)다.
우선순위 = 면접 빈도 × 헷갈리기 쉬운 정도. 각 레슨은 GitHub 이슈(`lesson` 라벨)로 관리한다.

```mermaid
flowchart LR
  TXB[트랜잭션 기초] --> CONN[DB 커넥션과 커넥션 풀]
  TXB --> ISO
  TXB --> TX
  BEAN[스프링 빈과 의존성 주입] --> TX
  TXB --> JPA0[JPA 첫걸음]
  BEAN --> JPA0
  JPA0 --> PC
  CONN --> OSIV
  PC[영속성 컨텍스트 ✅] --> PX[프록시와 지연 로딩 ✅]
  PX --> N1[N+1 문제 ✅]
  PC --> AM[연관관계 매핑 ✅]
  TX[@Transactional 동작 원리 ✅] --> TP[전파와 롤백 규칙 ✅]
  TX --> OSIV[OSIV ✅]
  PX --> OSIV
  ISO[격리 수준 ✅] --> LOCK[낙관적 락 vs 비관적 락 ✅]
  PC --> LOCK
  IDX[인덱스와 실행계획 ✅]
  TX --> IDEM[멱등성과 중복 요청 방지 ✅]
  TX --> BATCH[스프링 배치 ✅]
  ISO --> DML[대량 DML ✅]
  TX --> AOP[AOP와 감사 로그 ✅]
  PT[프로세스와 스레드 ✅] --> EX[스레드 풀과 ExecutorService ✅]
  PT --> FU[Future와 Callable ✅]
  EX --> FU
  FU --> CF[CompletableFuture ✅]
  PT --> SYNC[동기화: synchronized, volatile, 원자적 연산 ✅]
  SYNC --> LOCK
```

## 기초 레슨 (2026-11-16 추가)

처음 보는 사람 기준 검토(`docs/reviews/2026-11-16-beginner-review.md`)에서 여러 레슨이 공통으로 기대는 기초가 비어 있다는 결과가 나와 맨 앞에 추가했다. 대상 독자는 자바 문법·예외와 SQL 기초를 아는 사람이다.

| 순서 | 레슨 | 단원 | 선수 | 이슈 | 상태 |
| --- | --- | --- | --- | --- | --- |
| B1 | 트랜잭션 기초 | DB | 없음 | #40 | 대기 |
| B2 | DB 커넥션과 커넥션 풀 | DB | 트랜잭션 기초 | #41 | 대기 |
| B3 | 스프링 빈과 의존성 주입 | Spring | 없음 | #42 | 대기 |
| B4 | JPA 첫걸음 | JPA | 트랜잭션 기초, 스프링 빈 | #43 | 대기 |

## 제작 순서

4주 학습 계획(1주 멱등성·트랜잭션, 2주 동시성·락, 3주 조회 성능·인덱스, 4주 스프링 배치)과 KG 2차 면접 대비에 맞춘 순서. 레슨마다 PR을 열어 두고 다음 레슨으로 넘어가며, 검수와 병합은 몰아서 한다. 2026-11-15에 1~15번을 모두 공개했다.

| 순서 | 레슨 | 단원 | 선수 | 이슈 | 상태 |
| --- | --- | --- | --- | --- | --- |
| - | 영속성 컨텍스트 | JPA | JPA 첫걸음 | #1 | 공개 |
| - | N+1 문제 | JPA | 프록시와 지연 로딩 | #10 | 공개 |
| - | 프로세스와 스레드 | 운영체제 | 없음 | #12 | 공개 |
| - | Future와 Callable | 동시성 | 프로세스와 스레드 | #13 | 공개 |
| 1 | 스레드 풀과 ExecutorService | 동시성 | 프로세스와 스레드 | #14 | 공개 |
| 2 | @Transactional 동작 원리와 내부 호출 | Spring | 없음 | #2 | 공개 |
| 3 | 멱등성과 중복 요청 방지 | Spring | @Transactional | #18 | 공개 |
| 4 | 동기화 (synchronized, volatile, 원자적 연산) | 동시성 | 프로세스와 스레드 | #16 | 공개 |
| 5 | 격리 수준과 이상 현상 | DB | 없음 | #4 | 공개 |
| 6 | 낙관적 락 vs 비관적 락 | 동시성 | 격리 수준, 영속성 컨텍스트 | #5 | 공개 |
| 7 | 스프링 배치: 청크 처리와 재시작 | Batch | @Transactional | #19 | 공개 |
| 8 | 대량 DML 안전하게 하기 | DB | 격리 수준 | #20 | 공개 |
| 9 | CompletableFuture | 동시성 | Future와 Callable | #15 | 공개 |
| 10 | 인덱스와 실행계획 | DB | 없음 | #6 | 공개 |
| 11 | AOP와 감사 로그 | Spring | @Transactional | #21 | 공개 |
| 12 | 트랜잭션 전파와 롤백 규칙 | Spring | @Transactional | #3 | 공개 |
| 13 | 프록시와 지연 로딩 | JPA | 영속성 컨텍스트 | #7 | 공개 |
| 14 | OSIV | Spring | @Transactional, 프록시 | #8 | 공개 |
| 15 | 연관관계 주인과 양방향 매핑 | JPA | 영속성 컨텍스트 | #9 | 공개 |

KG파이낸셜 1차 면접에서 막힌 주제: 프로세스와 스레드, Future, 스프링 배치, 대량 DML, AOP와 감사 로그 (`priority:interview-gap` 라벨).
