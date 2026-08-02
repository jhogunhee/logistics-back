package com.project.wmsback.strategy.putaway.method;

import java.util.List;
import java.util.Optional;

/**
 * 적치 추천 방식. 후보 로케이션 선정만 담당한다 — 조건 필터·정렬·수량 분할은
 * 추천 서비스 몫이다. 상수 추가 = 메타 API(화면 선택지)·저장 검증·실행에 동시 반영이라
 * 코드에 없는 방식은 화면에 존재할 수 없다 (P1).
 * name()이 DB의 mthd_cd다 — 저장된 값이므로 상수명을 바꾸지 않는다 (은퇴는 deprecated).
 */
public enum PutawayMethod {

    /** 같은 상품 재고(on_hand>0)가 이미 있는 보관 로케이션을 후보로 */
    SAME_PROD_LOC("적재로케이션",
            "같은 상품의 재고가 이미 있는 보관 로케이션에 합쳐 적치합니다. 로케이션 파편화를 줄일 때 앞 단계로 둡니다.") {
        @Override
        public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx) {
            return ctx.storageLocs().stream().filter(PutawayMethodContext.LocStock::hasProd).toList();
        }
    },

    /** 재고가 전혀 없는 보관 로케이션을 후보로 */
    EMPTY_LOC("빈로케이션",
            "재고가 전혀 없는 보관 로케이션에 적치합니다. 새 로케이션을 여는 단계 — 보통 적재로케이션 뒤에 둡니다.") {
        @Override
        public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx) {
            return ctx.storageLocs().stream().filter(ls -> ls.occupiedQty() == 0).toList();
        }
    },

    /** 온도대 일치 보관 로케이션 전부 — 수동 후보 목록과 같은 모집단이라 마지막 단계의 안전망 용도 */
    ANY_LOC("전체 보관",
            "온도대가 맞는 모든 보관 로케이션을 후보로 합니다. 앞 단계에서 못 채운 잔여의 안전망으로 마지막에 둡니다.") {
        @Override
        public List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx) {
            return ctx.storageLocs();
        }
    };

    private final String label;
    private final String dscr;

    PutawayMethod(String label, String dscr) {
        this.label = label;
        this.dscr = dscr;
    }

    public String label() {
        return label;
    }

    public String dscr() {
        return dscr;
    }

    /** 은퇴 방식 — 화면 신규 선택 불가, 기존 정의는 계속 실행. 현재는 없다 */
    public boolean deprecated() {
        return false;
    }

    /** ctx의 보관 로케이션 재고 현황에서 이 방식의 후보를 고른다 */
    public abstract List<PutawayMethodContext.LocStock> candidates(PutawayMethodContext ctx);

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<PutawayMethod> find(String code) {
        for (PutawayMethod method : values()) {
            if (method.name().equals(code)) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    /**
     * 실행용 조회. 없으면 예외 — "저장된 정의가 배포본과 어긋남"을 뜻하며,
     * 저장 시 검증(P2) 때문에 정상 경로에서는 나올 수 없다 (운영 알람 대상).
     */
    public static PutawayMethod of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 전략 정의가 배포본과 어긋납니다 — 미등록 적치 방식: " + code));
    }
}
