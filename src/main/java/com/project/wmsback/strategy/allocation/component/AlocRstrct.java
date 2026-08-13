package com.project.wmsback.strategy.allocation.component;

import com.project.wmsback.strategy.allocation.field.AlocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AlocLineTarget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/**
 * 출고제약 구현체. 후보 재고를 <b>라인 단위로</b> 걸러낸다 — 같은 상품이라도 A점포엔 되는 Lot이
 * B점포엔 안 될 수 있어서, 판정 대상이 재고 하나가 아니라 (재고, 라인) 조합이다.
 *
 * <p>상수 추가 = 메타 API(화면 선택지)·저장 검증·실행에 동시 반영이라 코드에 없는 제약은
 * 화면에 존재할 수 없다 (P1). name()이 DB의 cmpnt_cd다 — 저장된 값이므로 상수명을 바꾸지 않는다.
 */
public enum AlocRstrct {

    /**
     * 잔여수명 비율 — 납품 시점에 유통기한이 얼마나 남았는지.
     *
     * <p><b>분모까지 Lot에서 뽑는다</b>({@code expiry_dt − mfg_dt}). 상품 마스터의
     * {@code shelf_life_days}를 분모로 쓰면 ① 분자는 Lot 스냅샷인데 분모가 마스터라
     * 마스터를 고치는 순간 기존 Lot 전체의 비율이 움직여 「소급 영향 없음」이 깨지고,
     * ② 벤더가 찍은 유통기한이 계산값과 다른 것이 정상 데이터라 비율이 100%를 넘는다.
     *
     * <p>입고 검수의 동명 규칙이 {@code shelf_life_days}를 그대로 쓰는 것과 <b>의도적으로 다르다</b> —
     * 검수는 Lot이 생성되는 그 시점에 판정하므로 스냅샷과 마스터가 같은 값이지만, 출고는 몇 달 뒤에
     * 재고를 보고 그 사이에 마스터 변경과 속성 정정이 끼어든다. 이름이 같고 구현이 다른 것이 옳다.
     *
     * <p>기준일은 <b>출고예정일</b>이다. 점포가 요구하는 것은 「납품 시점의 잔여수명」이라,
     * 할당 실행일로 재면 할당을 며칠 앞당길수록 통과 Lot이 늘어나 기준이 흔들린다.
     */
    SHELF_LIFE_PCT("잔여수명 비율",
            "납품 시점(출고예정일)에 남은 유통기한 비율이 기준 미만인 Lot을 후보에서 제외합니다. "
                    + "기준은 점포별(store.outb_life_rate) 또는 고정값 중에 고릅니다.") {

        @Override
        public Optional<String> reject(AlocInvnCandidate candidate, AlocLineTarget line,
                                       Map<String, Object> para) {
            BigDecimal rate = lifeRate(candidate, line);
            if (rate == null) {
                // 유통기한 미관리 Lot — 필터 대상이 아니다 (기한 경과 하드 가드는 별도로 걸린다)
                return Optional.empty();
            }
            long minPct = minPct(para, line);
            if (rate.compareTo(BigDecimal.valueOf(minPct)) >= 0) {
                return Optional.empty();
            }
            return Optional.of("잔여수명 " + rate + "% < 기준 " + minPct + "%");
        }

        @Override
        public void validatePara(Map<String, Object> para) {
            String basis = basis(para);
            if (!BASIS_STORE.equals(basis) && !BASIS_FIXED.equals(basis)) {
                throw new IllegalArgumentException(
                        "잔여수명 비율: 기준(basis)은 STORE(점포 기준) 또는 FIXED(고정값)여야 합니다.");
            }
            if (BASIS_FIXED.equals(basis)) {
                Object raw = para.get(PARA_MIN_PCT);
                if (!(raw instanceof Number number)) {
                    throw new IllegalArgumentException("잔여수명 비율: 고정 기준값(minPct)을 입력하세요.");
                }
                long value = number.longValue();
                if (value < 0 || value > 100) {
                    throw new IllegalArgumentException("잔여수명 비율: 고정 기준값은 0~100 사이여야 합니다.");
                }
            }
        }
    };

    public static final String PARA_BASIS = "basis";
    public static final String PARA_MIN_PCT = "minPct";
    public static final String BASIS_STORE = "STORE";
    public static final String BASIS_FIXED = "FIXED";

    private final String label;
    private final String dscr;

    AlocRstrct(String label, String dscr) {
        this.label = label;
        this.dscr = dscr;
    }

    public String label() {
        return label;
    }

    public String dscr() {
        return dscr;
    }

    /** 은퇴 구현체 — 화면 신규 선택 불가, 기존 정의는 계속 실행. 현재는 없다 */
    public boolean deprecated() {
        return false;
    }

    /** 제외 사유. empty면 통과 — 조용히 빠지는 재고를 만들지 않으려고 사유를 문자열로 돌려준다 */
    public abstract Optional<String> reject(AlocInvnCandidate candidate, AlocLineTarget line,
                                            Map<String, Object> para);

    /** 저장 시 파라미터 검증 (P2). 실패 = 저장 거부 */
    public abstract void validatePara(Map<String, Object> para);

    // ── 공통 계산 ────────────────────────────────────────────────────────────

    /**
     * 잔여수명 비율. 유통기한 미관리 Lot이거나 총수명일수를 낼 수 없으면 null (필터 대상 아님).
     * 소수 1자리 내림 — 경계에서 통과를 후하게 주지 않는다.
     *
     * <p>public인 이유: <b>수동할당 후보 화면이 같은 계산을 표시</b>하기 때문이다. 그쪽은 미달을
     * 차단하지 않고 경고만 하지만, 표시되는 비율과 자동할당이 거르는 비율이 다르면 화면을 믿을 수
     * 없게 된다 — 계산은 한 곳에만 있어야 한다.
     */
    public static BigDecimal lifeRate(AlocInvnCandidate candidate, AlocLineTarget line) {
        if (candidate.expiryDt() == null || candidate.mfgDt() == null) {
            return null;
        }
        long totalDays = ChronoUnit.DAYS.between(candidate.mfgDt(), candidate.expiryDt());
        if (totalDays <= 0) {
            return null;
        }
        long remainDays = ChronoUnit.DAYS.between(line.expctDe(), candidate.expiryDt());
        return BigDecimal.valueOf(remainDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalDays), 1, RoundingMode.DOWN);
    }

    static String basis(Map<String, Object> para) {
        Object raw = para != null ? para.get(PARA_BASIS) : null;
        // 기본은 점포 기준 — 현행 붙박이 동작과 같다
        return raw != null ? String.valueOf(raw) : BASIS_STORE;
    }

    static long minPct(Map<String, Object> para, AlocLineTarget line) {
        if (BASIS_FIXED.equals(basis(para)) && para.get(PARA_MIN_PCT) instanceof Number number) {
            return number.longValue();
        }
        return line.outbLifeRate();
    }

    /** 저장 시 검증용 조회 — 없으면 empty를 돌려주고 저장 서비스가 거부 메시지를 만든다 (P2) */
    public static Optional<AlocRstrct> find(String code) {
        for (AlocRstrct rstrct : values()) {
            if (rstrct.name().equals(code)) {
                return Optional.of(rstrct);
            }
        }
        return Optional.empty();
    }

    /** 실행용 조회. 없으면 "저장된 정의가 배포본과 어긋남" — 정상 경로에서는 나올 수 없다 */
    public static AlocRstrct of(String code) {
        return find(code).orElseThrow(() -> new IllegalStateException(
                "저장된 전략 정의가 배포본과 어긋납니다 — 미등록 출고제약: " + code));
    }
}
