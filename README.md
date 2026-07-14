# WMS Backend

입고 → 재고 → 출고 프로세스를 설계부터 구현까지 진행하는 WMS 백엔드.

물류 실무 경험을 바탕으로 프로세스 설계 판단(상태 전이, 재고 이력 모델, 할당 동시성)에 집중한다. 설계 근거는 [docs/design.md](docs/design.md) 참고.

## 기술 스택

- Java 17, Spring Boot 3.5, Spring Data JPA + QueryDSL
- Oracle Database Free (Docker)
- p6spy (SQL 로그)

## 실행

```bash
# 1. Oracle 기동 (Docker Desktop 실행 후)
docker compose up -d

# 2. 애플리케이션 실행
./mvnw spring-boot:run
```

DB 접속 정보 기본값은 `wms` / `wms1234` (`application.properties`에서 `DB_USERNAME`/`DB_PASSWORD` 환경변수로 재정의 가능).

## 진행 상태

- [x] 프로세스 설계 (상태 전이 / 재고 모델 / 할당 전략) — [docs/design.md](docs/design.md)
- [x] 프로젝트 초기 설정 (Boot + JPA + QueryDSL + Oracle)
- [ ] 마스터 데이터 (SKU / Location / Lot)
- [ ] 입고 (입고예정 → 검수/입고 → 마감 → 적치)
- [ ] 재고 (현재고 스냅샷 + 재고 이력, 이동/조정)
- [ ] 출고 (주문 → 할당 → 피킹 → 출고확정)
- [ ] 할당 동시성: 비관적/낙관적 락 비교 및 부하 측정
- [ ] 재고 대사(reconciliation) 배치
- [ ] 물동량 시뮬레이터 (시더)
- [ ] 대시보드 (별도 프론트 레포)