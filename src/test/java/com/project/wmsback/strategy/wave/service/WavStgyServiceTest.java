package com.project.wmsback.strategy.wave.service;

import com.project.wmsback.strategy.core.condition.ConditionOperator;
import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.core.service.StgyRvsnService;
import com.project.wmsback.strategy.wave.dto.WavStgyDefinition;
import com.project.wmsback.strategy.wave.repository.WavStgyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 저장 검증(P2) — "등록은 되는데 실행하면 모든 주문을 쓸어담는" 정의를 저장 시점에 거부한다 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WavStgyServiceTest {

    @Mock private WavStgyRepository wavStgyRepository;
    @Mock private StgyRvsnService stgyRvsnService;

    @InjectMocks private WavStgyService service;

    private WavStgyDefinition def(List<List<FieldCondition>> condGrp) {
        return new WavStgyDefinition("테스트 전략", 0, condGrp);
    }

    @Test
    @DisplayName("조건그룹 0건은 저장 거부")
    void rejectsNoGroups() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(List.of())));
    }

    @Test
    @DisplayName("빈 그룹은 저장 거부 — 조건 0건 그룹 하나로 미편성 주문 전부가 편입된다")
    void rejectsEmptyGroup() {
        List<List<FieldCondition>> grp = new ArrayList<>();
        grp.add(List.of());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.validate(def(grp)));
        assertTrue(e.getMessage().contains("비어 있습니다"));
    }

    @Test
    @DisplayName("없는 필드·허용되지 않는 연산자는 저장 거부")
    void rejectsUnknownFieldAndOperator() {
        // 납품처그룹·납품처유형은 보류라 필드 레지스트리에 없다 — 저장 단계에서 걸린다
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(
                List.of(List.of(new FieldCondition("STORE_GRP", ConditionOperator.EQ, List.of("G1")))))));
        // 코드값 필드에 대소 비교는 열지 않는다 (사전순이라 "10"이 "2"보다 앞선다)
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(
                List.of(List.of(new FieldCondition("VHCL_FLTNO", ConditionOperator.GE, List.of("1")))))));
    }

    @Test
    @DisplayName("연산자별 값 개수가 맞지 않으면 저장 거부 (EQ=1)")
    void rejectsWrongValueCount() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(def(
                List.of(List.of(new FieldCondition("OUTB_TYP", ConditionOperator.EQ, List.of()))))));
    }

    @Test
    @DisplayName("전략명 필수, 우선순위는 비우면 0으로 정규화")
    void normalizesDefinition() {
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                new WavStgyDefinition("  ", 0, List.of(List.of(
                        new FieldCondition("OUTB_TYP", ConditionOperator.EQ, List.of("NRML")))))));

        WavStgyDefinition normalized = service.validate(new WavStgyDefinition("정상", null,
                List.of(List.of(new FieldCondition("VHCL_FLTNO", ConditionOperator.IN, List.of("1", "2"))))));
        assertEquals(0, normalized.prty());
        assertEquals(1, normalized.condGrp().size());
    }
}
