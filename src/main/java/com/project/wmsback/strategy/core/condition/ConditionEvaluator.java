package com.project.wmsback.strategy.core.condition;

import java.util.List;
import java.util.Map;

/** 조건 목록의 저장 시 검증과 실행 시 판정. 두 곳이 같은 필드 레지스트리를 쓴다 */
public final class ConditionEvaluator {

    private ConditionEvaluator() {
    }

    /** 조건 전부(AND) 판정. 빈 목록 = 무조건 참 (적용대상 "전체 매칭") */
    public static <T> boolean matchesAll(List<FieldCondition> conds, Map<String, ? extends ConditionField<T>> fields, T target) {
        if (conds == null || conds.isEmpty()) {
            return true;
        }
        for (FieldCondition cond : conds) {
            ConditionField<T> field = fields.get(cond.fld());
            if (field == null) {
                // 저장 검증(P2)을 통과한 정의라면 나올 수 없다 — 정의/배포 불일치
                throw new IllegalStateException("저장된 조건이 배포본과 어긋납니다 — 미등록 필드: " + cond.fld());
            }
            if (!cond.op().test(field.extract(target), cond.vals())) {
                return false;
            }
        }
        return true;
    }

    /** 저장 시 검증: 필드 실존, 허용 연산자, 연산자별 값 개수. 실패 = 저장 거부 */
    public static void validate(String label, List<FieldCondition> conds, Map<String, ? extends ConditionField<?>> fields) {
        if (conds == null) {
            return;
        }
        for (FieldCondition cond : conds) {
            ConditionField<?> field = fields.get(cond.fld());
            if (field == null) {
                throw new IllegalArgumentException(label + ": 없는 조건 필드입니다 — " + cond.fld());
            }
            if (cond.op() == null) {
                throw new IllegalArgumentException(label + ": 연산자가 없습니다 — " + field.label());
            }
            if (!field.allowedOps().contains(cond.op())) {
                throw new IllegalArgumentException(label + ": " + field.label() + "에 허용되지 않는 연산자입니다 — " + cond.op());
            }
            int size = cond.vals() != null ? cond.vals().size() : 0;
            if (size < cond.op().minVals() || size > cond.op().maxVals()) {
                throw new IllegalArgumentException(label + ": " + field.label() + " 조건값 개수가 맞지 않습니다 ("
                        + cond.op() + "는 " + cond.op().minVals()
                        + (cond.op().maxVals() == Integer.MAX_VALUE ? "개 이상" : "~" + cond.op().maxVals() + "개") + ")");
            }
        }
    }
}
