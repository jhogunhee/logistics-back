package com.project.wmsback.strategy.allocation.component;

import com.project.wmsback.strategy.allocation.field.AlocLineTarget;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToLongFunction;

/**
 * 분배 구현체 — 재고가 그룹 총요청보다 <b>적을 때</b> 라인별 배분 상한을 산정한다.
 * 상태를 바꾸지 않는다: 산정만 하고 실제 배정은 산정기가 한다.
 *
 * <p>기본값은 {@link #SEQUENTIAL}이다. 분배 슬롯을 하나도 등록하지 않으면 순차 소진 1건과
 * 동치이고, 그것이 이 프로젝트의 원래 동작이다 — 부분할당을 허용하고 백오더가 없으므로
 * 나누면 모든 주문이 부분출고가 되어 「완전 출고 주문 수」가 줄어든다.
 * 비율·균등은 <b>한 번의 배분이 그대로 결과가 되는 경우</b>(행사·긴급 배분)를 위한 선택지다.
 *
 * <p>{@code name()}이 DB의 cmpnt_cd다 — 저장된 값이므로 상수명을 바꾸지 않는다.
 */
public enum AlocDstrb {

    /** 정렬 순서대로 채울 수 있는 만큼. 앞 라인이 다 가져간다 */
    SEQUENTIAL("순차 소진",
            "정렬 순서대로 채울 수 있는 만큼 배분합니다. 앞선 라인이 먼저 다 가져가므로 "
                    + "「완전 출고 주문 수」가 최대가 되고, 뒤 순번은 못 받을 수 있습니다.") {
        @Override
        Map<Long, Long> shares(long avalQty, List<AlocLineTarget> targets,
                               ToLongFunction<AlocLineTarget> room) {
            Map<Long, Long> shares = new LinkedHashMap<>();
            long left = avalQty;
            for (AlocLineTarget target : targets) {
                long take = Math.min(room.applyAsLong(target), left);
                shares.put(target.outbLineId(), take);
                left -= take;
            }
            return shares;
        }
    },

    /** 가용 × (라인 잔여요청 / 대상 라인 잔여요청 합) */
    RATIO("주문 비율",
            "주문수량 비율대로 나눕니다. 많이 주문한 라인이 많이 받고, 모두가 같은 비율로 "
                    + "부분출고됩니다.") {
        @Override
        Map<Long, Long> shares(long avalQty, List<AlocLineTarget> targets,
                               ToLongFunction<AlocLineTarget> room) {
            long total = 0;
            for (AlocLineTarget target : targets) {
                total += room.applyAsLong(target);
            }
            Map<Long, Long> shares = new LinkedHashMap<>();
            if (total <= 0) {
                return shares;
            }
            long assigned = 0;
            for (AlocLineTarget target : targets) {
                long share = avalQty * room.applyAsLong(target) / total;
                shares.put(target.outbLineId(), share);
                assigned += share;
            }
            spreadRemainder(shares, targets, avalQty - assigned);
            return shares;
        }
    },

    /** 대상 라인 수로 균등 */
    EQUAL("균등",
            "대상 라인에 같은 수량씩 나눕니다. 주문수량과 무관하게 똑같이 받으므로 "
                    + "소량 주문에 상대적으로 유리합니다.") {
        @Override
        Map<Long, Long> shares(long avalQty, List<AlocLineTarget> targets,
                               ToLongFunction<AlocLineTarget> room) {
            Map<Long, Long> shares = new LinkedHashMap<>();
            if (targets.isEmpty()) {
                return shares;
            }
            long base = avalQty / targets.size();
            for (AlocLineTarget target : targets) {
                shares.put(target.outbLineId(), base);
            }
            spreadRemainder(shares, targets, avalQty - base * targets.size());
            return shares;
        }
    };

    private final String label;
    private final String dscr;

    AlocDstrb(String label, String dscr) {
        this.label = label;
        this.dscr = dscr;
    }

    public String label() {
        return label;
    }

    public String dscr() {
        return dscr;
    }

    public boolean deprecated() {
        return false;
    }

    /**
     * 라인별 배분 상한을 산정한다. 입력 순서(= 주문 정렬 결과)가 나머지 배분과 순차 소진의
     * 우선권을 정하므로 <b>호출자가 정렬한 순서 그대로</b> 넘겨야 한다.
     *
     * @param avalQty 이 슬롯이 쓸 수 있는 가용수량
     * @param targets 이 슬롯의 대상 라인 (cond 충족분, 정렬된 순서)
     * @param room    라인의 남은 요청량 — 이미 배정된 분을 뺀 값
     * @return 라인 id → 배분 상한. 합계는 avalQty를 넘지 않는다
     */
    public Map<Long, Long> distribute(long avalQty, List<AlocLineTarget> targets,
                                      ToLongFunction<AlocLineTarget> room) {
        Map<Long, Long> caps = new LinkedHashMap<>();
        targets.forEach(target -> caps.put(target.outbLineId(), 0L));

        long left = avalQty;
        List<AlocLineTarget> active = new ArrayList<>(targets);
        // 잔여 재배분 루프. 산정치가 라인 잔여요청보다 크면 클램프되는데, 그 남은 몫을
        // 아직 여유 있는 대상 라인에 다시 나눈다 — 안 하면 "가용이 남았는데 배분이 끝나는"
        // 상태가 된다. 매 회차마다 누군가는 상한까지 차거나 left가 0이 되므로 반드시 끝난다.
        while (left > 0 && !active.isEmpty()) {
            long remaining = left;
            Map<Long, Long> shares = shares(remaining, active,
                    target -> room.applyAsLong(target) - caps.get(target.outbLineId()));

            boolean progressed = false;
            for (AlocLineTarget target : active) {
                long room4 = room.applyAsLong(target) - caps.get(target.outbLineId());
                long grant = Math.min(Math.min(shares.getOrDefault(target.outbLineId(), 0L), room4), left);
                if (grant > 0) {
                    caps.merge(target.outbLineId(), grant, Long::sum);
                    left -= grant;
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
            active.removeIf(target -> room.applyAsLong(target) - caps.get(target.outbLineId()) <= 0);
        }
        return caps;
    }

    /** 방식별 1회차 배분. 클램프·재배분은 {@link #distribute}가 공통으로 처리한다 */
    abstract Map<Long, Long> shares(long avalQty, List<AlocLineTarget> targets,
                                    ToLongFunction<AlocLineTarget> room);

    /** 내림 배분의 나머지를 앞에서부터 1씩. 정렬 순서가 여기서도 우선권이 된다 */
    private static void spreadRemainder(Map<Long, Long> shares, List<AlocLineTarget> targets, long remainder) {
        long left = remainder;
        for (AlocLineTarget target : targets) {
            if (left <= 0) {
                return;
            }
            shares.merge(target.outbLineId(), 1L, Long::sum);
            left--;
        }
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<AlocDstrb> find(String code) {
        for (AlocDstrb dstrb : values()) {
            if (dstrb.name().equals(code)) {
                return Optional.of(dstrb);
            }
        }
        return Optional.empty();
    }

    /** 실행용 조회. 없으면 "저장된 정의가 배포본과 어긋남" — 정상 경로에서는 나올 수 없다 */
    public static AlocDstrb of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 전략 정의가 배포본과 어긋납니다 — 미등록 분배 방식: " + code));
    }
}
