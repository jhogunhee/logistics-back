package com.project.mdm.prod.service;

import com.project.mdm.prod.entity.Prod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 미확정 주문이 있는 상품의 단위 구성 변경을 막는 가드. 검사 대상은 {@link ProdRefChecker#findOpenInbRef} 참고.
 * <p>
 * 단위 화면의 역할 이동과 낱개수량 수정이 같은 검사를 쓴다({@code ProdUomService}).
 */
@Component
@RequiredArgsConstructor
public class ProdUomChangeGuard {

    /** 환산 미실행 문서를 신고하는 앱들의 창구. @Order 순으로 주입된다 (WMS → OMS) */
    private final List<ProdRefChecker> checkers;

    /** 입고단위·그 낱개수량을 바꿔도 되는지. 걸리면 어느 문서 때문인지 담아 거부한다 */
    public void requireInbChangeable(Prod prod) {
        for (ProdRefChecker checker : checkers) {
            reject(checker.findOpenInbRef(prod.getId()), "입고단위", prod);
        }
    }

    /** 출고단위·그 낱개수량을 바꿔도 되는지 */
    public void requireOutbChangeable(Prod prod) {
        for (ProdRefChecker checker : checkers) {
            reject(checker.findOpenOutbRef(prod.getId()), "출고단위", prod);
        }
    }

    private void reject(String openRef, String label, Prod prod) {
        if (openRef != null) {
            throw new IllegalArgumentException(
                    "%s이 있어 %s 구성을 변경할 수 없습니다. 해당 문서를 먼저 확정하거나 삭제하세요: %s"
                            .formatted(openRef, label, prod.getProdNm()));
        }
    }
}
