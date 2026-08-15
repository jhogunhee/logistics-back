package com.project.mdm.prod.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.dto.ProdResponse;
import com.project.mdm.prod.dto.ProdSaveRequest;
import com.project.mdm.prod.dto.ProdSearchCond;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.repository.ProdRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProdService {

    private final ProdRepository prodRepository;
    private final NbrService nbrService;
    /** 상품을 참조하는 앱들의 신고 창구. @Order 순으로 주입된다 (WMS → OMS) */
    private final List<ProdRefChecker> prodRefCheckers;

    public List<ProdResponse> list(ProdSearchCond cond) {
        return prodRepository.search(cond).stream()
                .map(ProdResponse::from)
                .toList();
    }

    /** 신규(C)/수정(U)/삭제(D) 행 일괄 저장. 한 건이라도 실패하면 전체 롤백. */
    @Transactional
    public void saveAll(List<ProdSaveRequest> rows) {
        for (ProdSaveRequest row : rows) {
            switch (row.getStatus()) {
                case "C" -> create(row);
                case "U" -> update(row);
                case "D" -> delete(row);
                default -> throw new IllegalArgumentException("알 수 없는 행 상태입니다: " + row.getStatus());
            }
        }
        // 제약 위반(코드 중복 등)을 커밋 시점이 아니라 여기서 터뜨려 예외 변환이 되게 한다
        prodRepository.flush();
    }

    private void create(ProdSaveRequest row) {
        // 클라이언트가 보낸 코드는 받지 않는다 — 채번 규칙 PROD_CD로 발급 (PROD-0001 형식)
        prodRepository.save(row.toEntity(nbrService.issue("PROD_CD"))); // cascade로 포장까지 함께 저장
    }

    private void update(ProdSaveRequest row) {
        row.updateEntity(find(row.getProdId()));
    }

    /**
     * 물리삭제. 포장은 cascade로 함께 사라지지만, 재고·이력·문서가 참조 중이면 거부한다 —
     * FK가 0건이라 DB가 막아주지 않아서 그냥 지우면 그 행들이 없는 상품을 가리키게 된다.
     * 상품코드는 채번규칙을 따르고 있어서 되살릴 방법도 없다.
     * <p>
     * 참조 검사는 {@link ProdRefChecker} 구현체가 한다 — mdm은 자기 데이터를 누가 쓰는지 모른다.
     */
    private void delete(ProdSaveRequest row) {
        Prod prod = find(row.getProdId());
        String usedBy = findAnyReference(prod.getId());
        if (usedBy != null) {
            throw new IllegalArgumentException(
                    "%s에서 사용 중이라 삭제할 수 없습니다: %s".formatted(usedBy, prod.getProdNm()));
        }
        prodRepository.delete(prod);
    }

    /** 첫 참조에서 멈춘다 — 어느 앱이 먼저 걸리든 삭제가 막히는 결과는 같다 */
    private String findAnyReference(Long prodId) {
        for (ProdRefChecker checker : prodRefCheckers) {
            String usedBy = checker.findReference(prodId);
            if (usedBy != null) return usedBy;
        }
        return null;
    }

    private Prod find(Long prodId) {
        return prodRepository.findById(prodId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 상품입니다: " + prodId));
    }
}
