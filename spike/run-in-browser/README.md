# 시험 구현: 레슨에서 직접 돌려 보기 (2026-11-16)

GitHub Pages(서버 없음)에서 무료로 코드를 실행할 수 있는지 반나절 시험. 레슨에는 아직 붙이지 않았다.

실행: `node spike/run-in-browser/server.js` 후 `http://localhost:4810/cheerpj.html`, `/pglite.html`.
`ThreadPoolDemo.jar`는 `java/ThreadPoolDemo.java`를 `javac --release 8`로 빌드한 것.

## 1. CheerpJ 5.0 (브라우저 JVM): 지금은 쓰기 어렵다

| 확인 항목 | 결과 |
| --- | --- |
| (a) 라이선스 | Community 라이선스는 개인·교육·취미용 무료. 조건: CheerpJ CDN(`cjrtnc.leaningtech.com`)에서 로드해야 함(직접 호스팅은 유료), 키가 없으면 "COMMUNITY EDITION - FOR PERSONAL AND NON-BUSINESS USE ONLY" 표시. 비상업 오픈소스는 "Built with CheerpJ" 표기. 이 저장소(공개, 비상업 교육)는 해당. 회사 업무로 쓰면 유료 |
| (b) 첫 로딩 | **실행까지 108초**(Java 17, 탭 보이는 상태, 두 번째 방문). Java 8은 4분이 지나도 안 끝남. 원인: CheerpJ CDN이 이 PC에서 LA 엣지(`cf-ray …-LAX`)로 연결되어 약 70KB/s. 같은 PC에서 jsdelivr는 13MB를 1초 안에 받음. 무료 조건이 CDN 로드라서 우회할 수 없음 |
| (c) 결과 일치 | 풀 동작은 실제 JVM(11)과 **같음**: 스레드 1→2→2→2→3→4개, 큐 0→0→1→2→2→2, 작업 7 거절, 완료 6. Future.get도 정상. **시간은 다름**: 200ms 작업 4개 / 스레드 2개가 실제 JVM 411ms, CheerpJ 2107~3081ms. sleep·시간 측정이 들어간 예제(블로킹 비교 등)는 숫자를 믿을 수 없음 |
| (d) 휴대폰 | 확인 못 함. 데스크톱에서도 로딩이 2분 가까이라 휴대폰 확인 전에 탈락 |
| 기타 | 같은 페이지에서 `cheerpjRunMain`을 두 번째 부르면 멈춤(새로고침 필요) |

판단: 결과 자체는 정확하지만 한국에서 로딩 2분은 레슨 흐름(예측 → 바로 확인)에 맞지 않는다. 대안(외부 실행 API, 직접 실행 서버)은 요청대로 진행하지 않았다.

## 2. PGlite 0.5.16 (브라우저 PostgreSQL 18.6): DB 레슨에 바로 쓸 수 있다

| 확인 항목 | 결과 |
| --- | --- |
| 라이선스 | Apache 2.0 / PostgreSQL 라이선스, jsdelivr에서 로드 |
| 로딩 | 초기화 약 3초(약 13MB), 20만 행 생성 1.3초 |
| 인덱스 전후 EXPLAIN | 됨. EXPLAIN ANALYZE: 인덱스 전 Seq Scan 21ms → 후 Bitmap Index Scan 0.2ms. 선택도: status='PAID'(99%)는 Seq Scan, 'CANCELED'(1%)는 Index Scan. 인덱스 레슨의 주장과 같음 |
| 두 트랜잭션 동시 | **안 됨.** 연결이 하나라 A 트랜잭션이 커밋될 때까지 B 조회가 기다림(순서: A update → A commit → B read). 인스턴스를 두 개 만들면 서로 다른 빈 DB. 한 세션에서 BEGIN 두 번은 같은 트랜잭션 |

판단: 인덱스·실행계획, 대량 DML, 단일 트랜잭션 예제에는 붙일 수 있다. 격리 수준·락처럼 두 트랜잭션이 겹쳐야 하는 레슨은 지금처럼 시뮬레이터 + CI 검증 테스트로 간다.

## 3. Spring·JPA (아직 안 함)

- (a) CI에서 찍은 실제 SQL 로그를 저장해 레슨에 나란히 보여주기
- (b) 핵심 원리를 순수 자바로 다시 만들기(예: JDK 동적 프록시로 내부 호출 재현). 다만 실행기가 CheerpJ라면 1번의 로딩 문제를 그대로 가진다
- "저장소에서 이 테스트 돌려 보기"(테스트 경로와 명령)

실행기 방식이 정해지면 레슨 표준에 한 줄 추가한다.
