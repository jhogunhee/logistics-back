package com.project.wmsback.strategy.wave.service;

import com.project.wmsback.strategy.core.condition.FieldCondition;
import com.project.wmsback.strategy.wave.dto.WaveMatchResult;
import com.project.wmsback.strategy.wave.field.WaveOrderField;
import com.project.wmsback.strategy.wave.field.WaveOrderTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * 편성 판정 — 그룹끼리 OR, 그룹 안 AND.
 *
 * <p>자동실행·선택실행·미리보기가 <b>이 함수 하나</b>를 공유한다 (P4). 판정과 동시에 조건 단위
 * 근거(trace)를 만드는 이유는 "이 주문이 왜 탈락했나"가 실행 로그·미리보기의 본체이기 때문이다 —
 * 통과 여부만 반환하는 ConditionEvaluator.matchesAll을 쓰면 근거를 두 번 계산해야 한다.
 *
 * <p>첫 통과 그룹에서 멈추지 않고 <b>전 그룹을 평가</b>한다. 편입 여부는 어느 쪽이든 같지만,
 * 관리자가 "2번 그룹은 왜 안 걸렸나"까지 화면에서 볼 수 있어야 하기 때문이다.
 */
public final class WaveMatcher {

    private WaveMatcher() {
    }

    public static WaveMatchResult evaluate(List<List<FieldCondition>> condGrp, WaveOrderTarget target) {
        List<WaveMatchResult.GroupTrace> grps = new ArrayList<>();
        boolean matched = false;
        int idx = 0;
        for (List<FieldCondition> group : condGrp) {
            List<WaveMatchResult.CondTrace> conds = new ArrayList<>();
            boolean groupPass = true;
            for (FieldCondition cond : group) {
                WaveOrderField field = WaveOrderField.BY_CODE.get(cond.fld());
                if (field == null) {
                    // 저장 검증(P2)을 통과한 정의라면 나올 수 없다 — 정의/배포 불일치
                    throw new IllegalStateException("저장된 조건이 배포본과 어긋납니다 — 미등록 필드: " + cond.fld());
                }
                String actual = field.extract(target);
                boolean pass = cond.op().test(actual, cond.vals());
                conds.add(new WaveMatchResult.CondTrace(cond.fld(), cond.op().name(), cond.vals(), actual, pass));
                groupPass &= pass;
            }
            grps.add(new WaveMatchResult.GroupTrace(idx++, groupPass, conds));
            matched |= groupPass;
        }
        return new WaveMatchResult(target.outbOrderId(), target.outbNo(),
                target.outbTyp(), target.vhclFltno(), target.storeCd(),
                target.storeNm(), target.expctDe(), matched, grps);
    }
}
