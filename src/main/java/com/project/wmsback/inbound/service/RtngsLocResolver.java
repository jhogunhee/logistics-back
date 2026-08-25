package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.repository.LocRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 반품 검수의 불량 도착지 — 상품 온도대와 같은 반품존(biz_dvsn=RTNGS)의 첫 보관 로케이션.
 * RCV-STAGE처럼 코드값을 상수로 박지 않는다 — 온도대마다 자리가 다르고, 판정(inRtngsZon)도 여기 한 곳이다.
 */
@Component
@RequiredArgsConstructor
public class RtngsLocResolver {

    private final LocRepository locRepository;

    /**
     * 반품존 판정. 검수 취소·검수 이력의 판정 열·원천 대사가 쓴다.
     * 반품존은 검수만 넣고 재고이동만 뺀다 — 적치 후보·할당 후보 셋이 이 판정을 공유한다.
     */
    public static boolean inRtngsZon(Loc loc) {
        return loc.getZon() != null && loc.getZon().getBizDvsn() == BizDvsn.RTNGS;
    }

    public Loc resolve(Prod prod) {
        List<Loc> locs = locRepository.findRtngsLocs(prod.getTmpZon(), LocTyp.STORAGE, BizDvsn.RTNGS);
        if (locs.isEmpty()) {
            throw new IllegalStateException("온도대 " + prod.getTmpZon() + " 반품존 로케이션이 없습니다: " + prod.getProdCd());
        }
        return locs.get(0);
    }
}
