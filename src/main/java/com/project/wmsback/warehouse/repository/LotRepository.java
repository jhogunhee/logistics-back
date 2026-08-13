package com.project.wmsback.warehouse.repository;

import com.project.wmsback.warehouse.entity.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /**
     * 배치 키 충돌 검사(LotAttrChngService) 전용 — mfgDt가 non-null인 경로만 쓸 것.
     * 파생 쿼리라 null을 넘기면 mfg_dt = NULL로 바인딩되어 어떤 행도 매치되지 않는다(IS NULL 변환 없음).
     * 배치 재사용 조회는 null 분기를 갖춘 findAllByBatchKey(LotIssuer.find)로 옮겨갔다.
     */
    Optional<Lot> findByProdIdAndReceiptDtAndMfgDt(Long prodId, LocalDate receiptDt, LocalDate mfgDt);

    /**
     * 배치 재사용 키(상품+입고일자+제조일자) 조회 — LotIssuer 전용. 파생 쿼리를 쓰지 않는 이유:
     * 유통기한 미관리 상품은 제조일자가 항상 null인데 파생 쿼리는 null 매치가 안 되어(위 참고)
     * 증분 검수마다 새 Lot이 생기던 버그를 IS NOT DISTINCT FROM으로 고친다.
     * 네이티브인 이유: JPQL의 「:mfgDt is null」 분기는 PostgreSQL이 파라미터 타입을 추론하지
     * 못해 실행 시점에 터진다 — cast로 타입을 명시할 수 있는 네이티브가 안전하다.
     * 복수 매치는 그 버그가 이미 만들어 둔 중복 Lot이다 — lot_id 오름차순으로 돌려주고
     * 호출자(LotIssuer.find)가 최초 생성분을 결정적으로 재사용한다.
     */
    @Query(value = """
            select * from lot
            where prod_id = :prodId
              and receipt_dt = :receiptDt
              and mfg_dt is not distinct from cast(:mfgDt as date)
            order by lot_id asc
            """, nativeQuery = true)
    List<Lot> findAllByBatchKey(@Param("prodId") Long prodId, @Param("receiptDt") LocalDate receiptDt,
                                @Param("mfgDt") LocalDate mfgDt);

    /**
     * 속성 정정이 Lot 행을 갱신하기 전에 거는 비관적 락.
     * 정정은 이 락 앞에 상품 로우 락을 먼저 잡는다 — 검수(findOrCreateLot)와 같은 순서(상품 → Lot)라
     * 교착이 없고, 배치 재사용 키(상품+입고일자+제조일자) 판정이 두 업무 사이에서 직렬화된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.id = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") Long id);

    /** 상품+입고일자 기준 다음 채번 번호 계산용 (일자 리셋) */
    long countByProdIdAndReceiptDt(Long prodId, LocalDate receiptDt);

    /** 상품별 Lot 목록 (유통기한 빠른 순 — 미관리 Lot은 뒤로). 재고조사 라인 수동 추가의 Lot 선택용 */
    List<Lot> findByProdIdOrderByExpiryDtAscLotNoAsc(Long prodId);
}
