# 로드맵

첫 목표는 백엔드 면접에서 자주 나오는 JPA·Spring·DB·Java 동시성 개념. 실제 면접에서 답하지 못한 주제를 가장 먼저 만든다. 화살표는 선수 관계(먼저 배우면 좋은 레슨)다.
우선순위 = 면접 빈도 × 헷갈리기 쉬운 정도. 각 레슨은 GitHub 이슈(`lesson` 라벨)로 관리한다.

```mermaid
flowchart LR
  PC[영속성 컨텍스트] --> PX[프록시와 지연 로딩]
  PX --> N1[N+1 문제 ✅]
  PC --> AM[연관관계 매핑]
  TX[@Transactional 동작 원리] --> TP[전파와 롤백 규칙]
  TX --> OSIV[OSIV]
  PX --> OSIV
  ISO[격리 수준] --> LOCK[낙관적 락 vs 비관적 락]
  PC --> LOCK
  IDX[인덱스와 실행계획]
  PT[프로세스와 스레드] --> EX[스레드 풀과 ExecutorService]
  PT --> FU[Future와 Callable]
  EX --> FU
  FU --> CF[CompletableFuture]
  PT --> SYNC[동기화: synchronized, volatile, 원자적 연산]
  SYNC --> LOCK
```

| 레슨 | 단원 | 선수 | 우선순위 | 상태 |
| --- | --- | --- | --- | --- |
| 프로세스와 스레드 | 운영체제 | 없음 | 최우선 (면접 오답) | 진행 중 |
| Future와 Callable | 동시성 | 프로세스와 스레드 | 최우선 (면접 오답) | 진행 중 |
| 스레드 풀과 ExecutorService | 동시성 | 프로세스와 스레드 | 높음 | 대기 |
| CompletableFuture | 동시성 | Future와 Callable | 높음 | 대기 |
| 동기화 (synchronized, volatile, 원자적 연산) | 동시성 | 프로세스와 스레드 | 높음 | 대기 |
| N+1 문제 | JPA | 프록시와 지연 로딩 | 높음 | 공개 |
| 영속성 컨텍스트 (1차 캐시, 쓰기 지연, 변경 감지) | JPA | 없음 | 높음 | 공개 |
| @Transactional 동작 원리와 내부 호출 | Spring | 없음 | 높음 | 테스트 있음, 레슨 없음 |
| 트랜잭션 전파와 롤백 규칙 | Spring | @Transactional | 높음 | 대기 |
| 격리 수준과 이상 현상 | DB | 없음 | 높음 | 대기 |
| 낙관적 락 vs 비관적 락 | 동시성 | 격리 수준, 영속성 컨텍스트 | 높음 | 대기 |
| 인덱스와 실행계획 | DB | 없음 | 높음 | 대기 |
| 프록시와 지연 로딩 | JPA | 영속성 컨텍스트 | 중간 | 대기 |
| OSIV | Spring | @Transactional, 프록시 | 중간 | 대기 |
| 연관관계 주인과 양방향 매핑 | JPA | 영속성 컨텍스트 | 중간 | 대기 |
