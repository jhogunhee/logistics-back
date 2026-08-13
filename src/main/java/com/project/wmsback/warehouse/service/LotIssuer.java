package com.project.wmsback.warehouse.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.wmsback.warehouse.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Lot 채번·배치 재사용의 단일 창구. 배치 재사용 키(상품+입고일자+제조일자)의 정의는 여기 한 곳뿐이다 —
 * 검수(ReceivingService)와 재고 로트변경이 같은 규칙을 써야 해서 ReceivingService의 private을 밖으로 뺐다.
 * 복사하면 키 정의가 두 벌이 되는데, 로트변경이 존재하는 이유 자체가 그 키 하나다.
 *
 * <p>세 메서드 모두 <b>호출자가 잡아 둔 상품 로우 락 안에서만</b> 부를 것 — 채번(nextLotNo, 건수+1)과
 * 「재사용 조회 → 채번 → 저장」의 직렬화가 전부 그 락에 얹혀 있다.
 */
@Component
@RequiredArgsConstructor
public class LotIssuer {

    /** Lot 번호의 입고일자 조각 형식 — 채번 근거는 nextLotNo 참고 */
    private static final DateTimeFormatter LOT_NO_DT_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final LotRepository lotRepository;

    /**
     * 배치 재사용 키 조회. 유통기한 미관리 상품(mfgDt null)도 null 배치로 매치된다 — 파생 쿼리의
     * null 바인딩 버그를 여기서 고쳤다(LotRepository.findAllByBatchKey 참고). 그 버그가 이미 만들어 둔
     * 같은 키의 중복 Lot이 있으면 최초 생성분(최소 lot_id)을 결정적으로 재사용한다 — 중복을 병합하지는
     * 않지만(재고·이력이 참조 중) 더 늘리지도 않는다.
     */
    public Optional<Lot> find(Prod prod, LocalDate receiptDt, LocalDate mfgDt) {
        return lotRepository.findAllByBatchKey(prod.getId(), receiptDt, mfgDt).stream().findFirst();
    }

    /** 새 Lot 채번·생성. 유통기한은 호출자가 정한다 — 검수는 계산값(findOrCreate), 로트변경은 화면 입력값(벤더 인쇄값 정정이 주 사용처) */
    public Lot create(Prod prod, LocalDate receiptDt, LocalDate mfgDt, LocalDate expiryDt) {
        return lotRepository.save(Lot.builder()
                .prod(prod)
                .lotNo(nextLotNo(prod, receiptDt))
                .receiptDt(receiptDt)
                .mfgDt(mfgDt)
                .expiryDt(expiryDt)
                .build());
    }

    /**
     * 검수 전용 — 같은 배치는 재사용하고, 없으면 유통기한을 mfgDt + shelfLifeDays로 계산해 생성한다
     * (증분 검수로 같은 라인을 여러 번 나눠 검수해도 Lot이 쪼개지지 않도록. 미관리 상품은 두 날짜 null).
     * 로트변경은 이 메서드를 쓰지 않는다 — 재사용 경로에서 넘긴 유통기한이 무시되는 것이 검수에서는 당연하지만
     * 로트변경에서는 「입력과 다른 값의 조용한 저장」이라, find()로 조회해 유통기한을 검사한 뒤 create()를 직접 부른다.
     */
    public Lot findOrCreate(Prod prod, LocalDate receiptDt, LocalDate mfgDt) {
        return find(prod, receiptDt, mfgDt)
                .orElseGet(() -> create(prod, receiptDt, mfgDt,
                        mfgDt != null ? mfgDt.plusDays(prod.getShelfLifeDays()) : null));
    }

    /**
     * Lot 번호 채번: LOT-{입고일자}-{순번}. 순번은 상품별·입고일자별로 1부터 —
     * "이 상품이 그날 몇 번째 배치인가"라는 뜻이고, 유일성 단위도 uq_lot(prod_id, lot_no)라 이걸로 충분하다.
     * 로트변경이 만드는 Lot도 제조일자가 다른 별개 배치이므로 그날의 배치가 하나 늘어난 것이 맞다.
     * <p>
     * NbrService를 쓰지 않는 이유: 채번 규칙의 리셋 단위는 날짜뿐이라 상품별 리셋을 표현할 수 없고
     * (순번이 그날 창고 전체 통번이 되어 위 의미가 사라진다), nbr_seq 카운터 행이 상품이 달라도
     * 부딪히는 전역 직렬화 지점이 된다. 여기 채번은 호출자가 잡아 둔 상품 로우 락에 얹혀
     * 상품 단위로만 직렬화된다. 건수+1이 안전한 것은 Lot이 삭제되지 않아 단조 증가이기 때문이다 —
     * 삭제가 생기면 번호가 재사용되니 그때는 기존 번호의 최대 순번 파싱으로 바꿀 것(uq_lot이 최후 방어).
     */
    private String nextLotNo(Prod prod, LocalDate receiptDt) {
        long seq = lotRepository.countByProdIdAndReceiptDt(prod.getId(), receiptDt) + 1;
        return String.format("LOT-%s-%03d", receiptDt.format(LOT_NO_DT_FMT), seq);
    }
}
