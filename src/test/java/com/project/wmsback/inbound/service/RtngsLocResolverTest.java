package com.project.wmsback.inbound.service;

import com.project.mdm.prod.entity.Prod;
import com.project.mdm.prod.entity.TmpZon;
import com.project.wmsback.warehouse.entity.BizDvsn;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Zon;
import com.project.wmsback.warehouse.repository.LocRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 불량 도착지는 상수가 아니라 해석이다 — 상품 온도대와 같은 반품존의 첫 STORAGE 로케이션. */
class RtngsLocResolverTest {

    private final LocRepository locRepository = mock(LocRepository.class);
    private final RtngsLocResolver resolver = new RtngsLocResolver(locRepository);

    private Prod prod(TmpZon tmpZon) {
        Prod prod = mock(Prod.class);
        when(prod.getTmpZon()).thenReturn(tmpZon);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        return prod;
    }

    @Test
    @DisplayName("온도대가 같은 반품존의 첫 로케이션")
    void resolvesFirstLocOfMatchingZone() {
        Loc first = mock(Loc.class);
        when(locRepository.findRtngsLocs(TmpZon.CHL, LocTyp.STORAGE, BizDvsn.RTNGS)).thenReturn(List.of(first, mock(Loc.class)));

        assertSame(first, resolver.resolve(prod(TmpZon.CHL)));
    }

    @Test
    @DisplayName("반품존이 없으면 예외 — 불량을 받을 자리가 없다")
    void throwsWhenNoZone() {
        when(locRepository.findRtngsLocs(TmpZon.FRZ, LocTyp.STORAGE, BizDvsn.RTNGS)).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> resolver.resolve(prod(TmpZon.FRZ)));
    }

    @Test
    @DisplayName("반품존 판정 — 존이 없거나 업무구분이 다르면 아니다")
    void inRtngsZon() {
        Zon rtngs = mock(Zon.class);
        when(rtngs.getBizDvsn()).thenReturn(BizDvsn.RTNGS);
        Zon storage = mock(Zon.class);
        when(storage.getBizDvsn()).thenReturn(BizDvsn.STRG);
        Loc a = mock(Loc.class); when(a.getZon()).thenReturn(rtngs);
        Loc b = mock(Loc.class); when(b.getZon()).thenReturn(storage);
        Loc c = mock(Loc.class); when(c.getZon()).thenReturn(null);

        assertTrue(RtngsLocResolver.inRtngsZon(a));
        assertFalse(RtngsLocResolver.inRtngsZon(b));
        assertFalse(RtngsLocResolver.inRtngsZon(c));
    }
}
