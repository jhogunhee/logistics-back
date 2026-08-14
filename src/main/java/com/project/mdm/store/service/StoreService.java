package com.project.mdm.store.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.store.dto.StoreResponse;
import com.project.mdm.store.dto.StoreSaveRequest;
import com.project.mdm.store.dto.StoreSearchCond;
import com.project.mdm.store.entity.Store;
import com.project.mdm.store.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final NbrService nbrService;
    /** 점포를 참조하는 앱들의 신고 창구. @Order 순으로 주입된다 (WMS → OMS) */
    private final List<StoreRefChecker> storeRefCheckers;

    /** 목록 (점포코드 순). 마스터라 건수가 적어 페이징 없이 내려준다. 빈 조건이면 전체 —
     *  출고주문의 납품처 선택 팝업도 이 경로를 그대로 쓴다 */
    public List<StoreResponse> list(StoreSearchCond cond) {
        return storeRepository.search(cond).stream()
                .map(StoreResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<StoreSaveRequest> rows) {
        for (StoreSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> { validate(row); create(row); }
                case "U" -> { validate(row); update(row); }
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        storeRepository.flush();
    }

    private void create(StoreSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 STORE_CD로 발급 (ST-0001 형식)
        String storeCd = nbrService.issue("STORE_CD");
        storeRepository.save(Store.builder()
                .storeCd(storeCd)
                .storeNm(row.getStoreNm())
                .storeGrp(row.getStoreGrp())
                .storeTyp(row.getStoreTyp())
                .outbLifeRate(row.getOutbLifeRate())
                .build());
    }

    private void update(StoreSaveRequest row) {
        Store store = storeRepository.findById(row.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점포입니다: " + row.getStoreId()));
        store.update(row.getStoreNm(), row.getStoreGrp(), row.getStoreTyp(), row.getOutbLifeRate());
    }

    /**
     * 물리삭제. 출고주문이 참조 중이면 거부한다 — FK가 0건이라 DB가 막아주지 않아서
     * 그냥 지우면 주문이 없는 점포를 가리키게 되고 할당의 잔여수명 필터도 기준을 잃는다.
     * <p>
     * 참조 검사는 {@link StoreRefChecker} 구현체가 한다 — mdm은 자기 데이터를 누가 쓰는지 모른다.
     */
    private void delete(StoreSaveRequest row) {
        Store store = storeRepository.findById(row.getStoreId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 점포입니다: " + row.getStoreId()));
        String usedBy = findAnyReference(store.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, store.getStoreNm()));
        }
        storeRepository.delete(store);
    }

    /** 첫 참조에서 멈춘다 — 어느 앱이 먼저 걸리든 삭제가 막히는 결과는 같다 */
    private String findAnyReference(Long storeId) {
        for (StoreRefChecker checker : storeRefCheckers) {
            String usedBy = checker.findReference(storeId);
            if (usedBy != null) return usedBy;
        }
        return null;
    }

    private void validate(StoreSaveRequest row) {
        if (row.getStoreNm() == null || row.getStoreNm().isBlank()) {
            throw new IllegalArgumentException("점포명은 필수입니다.");
        }
        // 그리드 숫자 셀을 비우면 null로 넘어온다 — DB NOT NULL·CHECK(0~100)에 맡기면
        // 409 "다른 데이터가 참조하고 있어…" 라는 엉뚱한 메시지가 나가므로 여기서 먼저 막는다
        if (row.getOutbLifeRate() == null) {
            throw new IllegalArgumentException("잔여수명 허용률은 필수입니다: " + row.getStoreNm());
        }
        if (row.getOutbLifeRate() < 0 || row.getOutbLifeRate() > 100) {
            throw new IllegalArgumentException("잔여수명 허용률은 0~100 사이여야 합니다: " + row.getStoreNm());
        }
    }
}
