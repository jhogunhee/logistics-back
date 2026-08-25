package com.project.wmsback.inventory.service;

import com.project.mdm.nbr.service.NbrService;
import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.repository.InvHldAcrstRepository;
import com.project.wmsback.inventory.repository.InvHldRepository;
import com.project.wmsback.inventory.repository.InvHldRlzAcrstRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 보류 한 건의 등록 단위(holdOn) — 화면 등록과 반품 검수가 같이 쓴다. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvHldServiceTest {

    @Mock InvStore invStore;
    @Mock InvHldRepository invHldRepository;
    @Mock InvHldAcrstRepository invHldAcrstRepository;
    @Mock InvHldRlzAcrstRepository invHldRlzAcrstRepository;
    @Mock RsnValidator rsnValidator;
    @Mock NbrService nbrService;

    private InvHldService service;
    private Inv inv;
    private Loc loc;

    @BeforeEach
    void setUp() {
        service = new InvHldService(invStore, invHldRepository, invHldAcrstRepository, invHldRlzAcrstRepository,
                rsnValidator, nbrService);
        Prod prod = mock(Prod.class);
        when(prod.getProdCd()).thenReturn("PROD-0001");
        loc = mock(Loc.class);
        when(loc.getLocTyp()).thenReturn(LocTyp.STORAGE);
        when(loc.getLocCd()).thenReturn("RTN-DRY-01");
        inv = mock(Inv.class);
        when(inv.getProd()).thenReturn(prod);
        when(inv.getLoc()).thenReturn(loc);
        when(inv.getLot()).thenReturn(mock(Lot.class));
        when(inv.avalQty()).thenReturn(100L);
        when(rsnValidator.validate(eq("HLD_RSN"), any(), eq("DAMG"), any())).thenReturn(null);
        when(nbrService.issue(eq("HLD_NO"), any())).thenReturn("HD-20260825-001");
    }

    @Test
    @DisplayName("가용 이내면 hld 증가 + 보류 건 + 실적 저장, 보류번호 반환")
    void holdsAndRecords() {
        String hldNo = service.holdOn(inv, 40, "DAMG", null);

        assertEquals("HD-20260825-001", hldNo);
        verify(invStore).hold(inv, 40);
        ArgumentCaptor<InvHld> captor = ArgumentCaptor.forClass(InvHld.class);
        verify(invHldRepository).save(captor.capture());
        assertEquals(40L, captor.getValue().getHldQty());
        assertEquals("DAMG", captor.getValue().getRsnCd());
        verify(invHldAcrstRepository).save(any());
    }

    @Test
    @DisplayName("가용을 넘으면 거부하고 아무것도 저장하지 않는다")
    void rejectsOverAval() {
        assertThrows(IllegalArgumentException.class, () -> service.holdOn(inv, 101, "DAMG", null));
        verify(invStore, never()).hold(any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("보관 로케이션이 아니면 거부")
    void rejectsNonStorage() {
        when(loc.getLocTyp()).thenReturn(LocTyp.STAGE);
        assertThrows(IllegalArgumentException.class, () -> service.holdOn(inv, 1, "DAMG", null));
    }
}
