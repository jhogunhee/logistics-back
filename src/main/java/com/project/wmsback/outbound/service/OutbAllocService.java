package com.project.wmsback.outbound.service;

import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.service.InvStore;
import com.project.wmsback.outbound.dto.AllocCandidateResponse;
import com.project.wmsback.outbound.dto.AllocExecuteRequest;
import com.project.wmsback.outbound.dto.AllocExecuteResponse;
import com.project.wmsback.outbound.dto.AllocReleaseRequest;
import com.project.wmsback.outbound.dto.AllocTargetSearchCond;
import com.project.wmsback.outbound.dto.AllocWaveDetailResponse;
import com.project.wmsback.outbound.dto.AllocWaveResponse;
import com.project.wmsback.outbound.dto.ManualAllocRequest;
import com.project.wmsback.outbound.entity.OutbAlloc;
import com.project.wmsback.outbound.entity.OutbLine;
import com.project.wmsback.outbound.entity.OutbStatus;
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.entity.PikngTask;
import com.project.wmsback.outbound.entity.PikngTaskStatus;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.outbound.repository.PikngTaskRepository;
import com.project.wmsback.strategy.allocation.component.AlocRstrct;
import com.project.wmsback.strategy.allocation.dto.AlocDecisionTrace;
import com.project.wmsback.strategy.allocation.dto.AlocStgyResponse;
import com.project.wmsback.strategy.allocation.dto.AlocStgyDefinition;
import com.project.wmsback.strategy.allocation.dto.AlocGroupPlan;
import com.project.wmsback.strategy.allocation.entity.AlocSlotTyp;
import com.project.wmsback.strategy.allocation.entity.AlocStgy;
import com.project.wmsback.strategy.allocation.field.AlocInvnCandidate;
import com.project.wmsback.strategy.allocation.field.AlocLineTarget;
import com.project.wmsback.strategy.allocation.repository.AlocQueryRepository;
import com.project.wmsback.strategy.allocation.service.AlocPlanner;
import com.project.wmsback.strategy.allocation.service.AlocStgyService;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.entity.TrgrTyp;
import com.project.wmsback.strategy.core.service.StgyExecLogService;
import com.project.wmsback.warehouse.entity.LocTyp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 재고 할당 — <b>웨이브를 대상으로 실행해서, 그 안의 출고주문 라인을 채운다.</b>
 *
 * <p>계층이 셋이고 각각이 하는 일이 다르다:
 * <ul>
 *   <li><b>웨이브</b> — 화면 선택과 실행 파라미터. 웨이브 상태는 바뀌지 않는다({@code PLANNED → ISSUED} 둘뿐)</li>
 *   <li><b>상품</b> — 후보 조회와 락의 그룹 단위. 같은 상품의 여러 라인이 후보 리스트 하나를 순서대로 소진한다</li>
 *   <li><b>라인</b> — 요청수량과 할당 결과({@code outb_alloc})</li>
 * </ul>
 *
 * <p><b>동기 · 단일 트랜잭션이다.</b> 비동기로 가면 진행 플래그·상품별 병렬·부분 성공이 줄줄이
 * 따라오고, 결과를 화면에 돌려줄 수 없어 실패 원인을 로그에서 찾게 된다. 웨이브 하나가
 * 주문 수십 건이라도 동기로 충분하다.
 *
 * <p><b>재고 부족은 실패가 아니다</b> — 부분할당으로 정상 종료하고 잔량은 파생값으로 보여준다.
 * 결품 테이블도 사유코드도 두지 않는다(docs/design.md 「재고 할당」).
 *
 * <p><b>「무엇을 얼마나」는 전략이, 「어떻게 안전하게 쓰는가」는 이 서비스가 정한다.</b>
 * 후보 선정·정렬·배분은 {@link AlocPlanner}에 있는 순수 산정으로 빠졌고, 여기 남은 것은
 * 락 순서 · 예약 반영 · 트랜잭션 경계다. 전략이 하나도 없으면 산정기가 기본 동작(FEFO ·
 * 점포 잔여수명 · 순차 소진)으로 돌아 <b>전략 도입 전과 결과가 같다.</b>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbAllocService {

    private final OutbAllocRepository outbAllocRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final OutbLineRepository outbLineRepository;
    private final PikngTaskRepository pikngTaskRepository;
    private final InvStore invStore;
    private final AlocStgyService alocStgyService;
    private final AlocQueryRepository alocQueryRepository;
    private final StgyExecLogService stgyExecLogService;

    // ── 조회 ─────────────────────────────────────────────────────────────────

    public List<AllocWaveResponse> searchTargetWaves(AllocTargetSearchCond cond) {
        return outbAllocRepository.searchTargetWaves(cond);
    }

    public AllocWaveDetailResponse detail(Long wavId) {
        OutbWave wave = findWave(wavId);
        return new AllocWaveDetailResponse(wave.getId(), wave.getWavNo(),
                outbAllocRepository.lineRows(wavId), outbAllocRepository.allocRows(wavId));
    }

    /**
     * 수동할당 후보 재고. 자동할당과 같은 후보 집합이되 <b>잔여수명 미달을 걸러내지 않고 표시만 한다</b> —
     * 수동할당의 존재 이유가 예외 처리라 사람이 보고 판단해야 한다.
     * 기한이 지난 Lot만은 여기서도 뺀다(비율과 무관한 하드 가드).
     *
     * <p>비율 계산({@link AlocRstrct#lifeRate})도 <b>통과 판정</b>({@link AlocPlanner#rstrctReason})도
     * 자동할당이 쓰는 것을 그대로 쓴다 — 화면에 보이는 기준과 자동할당이 거르는 기준이 다르면
     * 화면을 믿을 수 없게 된다(전략이 고정 기준값을 쓰면 화면도 그 값으로 판정한다).
     */
    public List<AllocCandidateResponse> candidates(Long outbLineId) {
        OutbLine line = findLine(outbLineId);
        AlocLineTarget target = AlocLineTarget.of(line, 0L);

        // 전략도 실행과 같은 창구로 고른다. 다만 선택 규칙이 「대상 라인 전부 만족」이라,
        // 대상이 이 라인 하나인 여기서는 웨이브 전체로 고른 전략과 다를 수 있다
        AlocStgy stgy = alocStgyService.select(List.of(target)).orElse(null);
        AlocStgyDefinition def = stgy != null ? AlocStgyResponse.from(stgy).toDefinition() : null;
        List<AlocStgyDefinition.SlotDef> rstrctSlots = def != null ? def.slotsOf(AlocSlotTyp.RSTRCT) : List.of();
        // 계층(재고위치) 축도 자동할당과 같은 판정을 내린다 — 제약만 보여 주면 「잔여수명은 초록인데
        // 자동할당은 절대 안 건드리는」 재고가 화면에서 구분되지 않는다
        List<AlocStgyDefinition.SlotDef> tiers = def != null ? def.slotsOf(AlocSlotTyp.INVN_FLTR) : List.of();
        Map<Long, String> bizDvsnByZon = alocQueryRepository.bizDvsnByZon();

        List<AllocCandidateResponse> result = new ArrayList<>();
        for (Inv candidate : outbAllocRepository.findCandidates(line.getProd().getId())) {
            if (AlocPlanner.expired(candidate.getLot().getExpiryDt(), target.expctDe())) {
                continue;
            }
            AlocInvnCandidate snapshot = AlocInvnCandidate.of(candidate,
                    bizDvsnByZon.get(candidate.getLoc().getZon().getId()));
            BigDecimal rate = AlocRstrct.lifeRate(snapshot, target);
            String rjctRsn = AlocPlanner.rstrctReason(rstrctSlots, snapshot, target);
            Integer tierSeq = AlocPlanner.tierSeq(tiers, snapshot);
            result.add(new AllocCandidateResponse(
                    candidate.getId(),
                    candidate.getLoc().getId(), candidate.getLoc().getLocCd(),
                    candidate.getLot().getId(), candidate.getLot().getLotNo(),
                    candidate.getLot().getMfgDt(), candidate.getLot().getExpiryDt(),
                    candidate.getOnHandQty(), candidate.avalQty(),
                    rate, rjctRsn == null, rjctRsn,
                    tierSeq, tierCondOf(tiers, tierSeq)));
        }
        return result;
    }

    /** 속한 계층의 조건 한 줄. 계층 정의가 없으면 "전체", 어느 계층에도 안 속하면 null */
    private static String tierCondOf(List<AlocStgyDefinition.SlotDef> tiers, Integer tierSeq) {
        if (tierSeq == null) {
            return null;
        }
        if (tiers.isEmpty()) {
            return "전체";
        }
        return AlocPlanner.describeCond(tiers.get(tierSeq - 1).condOrEmpty());
    }

    // ── 자동할당 ──────────────────────────────────────────────────────────────

    /**
     * 웨이브 자동할당. 여러 웨이브를 한 번에 실행할 수 있지만 <b>한 트랜잭션</b>이다 —
     * 도중 실패하면 이번 실행 전체가 롤백된다(부분 성공 없음).
     */
    @Transactional
    public AllocExecuteResponse execute(AllocExecuteRequest request) {
        List<Long> wavIds = distinct(request.getWavIds());
        if (wavIds.isEmpty()) {
            throw new IllegalArgumentException("할당할 웨이브를 선택하세요.");
        }
        List<String> wavNos = new ArrayList<>();
        for (Long wavId : wavIds) {
            // 웨이브 행 락 — 같은 웨이브의 동시 실행을 직렬화한다. 라인별 기할당 합(alreadyByLine)을
            // 재고 락보다 먼저 읽으므로, 이 락이 없으면 동시 실행 둘이 같은 잔여요청을 보고 각자
            // 예약해 라인 과할당(SUM(aloc_qty) > odr_qty)이 난다. distinct()가 wavId를 오름차순으로
            // 정렬해 주므로 다건 실행끼리도 교착이 없다.
            // 웨이브 상태는 보지 않는다 — 발행된 웨이브도 잔량이 남으면 할당 대상이다.
            // 「지시 없는 할당」은 금지가 아니라 정상 상태이고(추가 발행이 소진한다),
            // 「할당 없는 지시」는 할당해제 가드가 여전히 막는다
            OutbWave wave = lockWave(wavId);
            wavNos.add(wave.getWavNo());
        }

        List<OutbLine> lines = outbAllocRepository.findTargetLines(wavIds);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("할당할 잔량이 남은 라인이 없습니다.");
        }
        return allocate(lines, wavNos);
    }

    /**
     * 할당 본체. 상품별로 모아 후보를 한 번만 조회·락하고, 산정기가 낸 계획을 예약으로 반영한다.
     *
     * <p><b>전략은 실행 1회당 1건</b>이다 — 상품 그룹이 웨이브를 가로지르므로(여러 웨이브의 같은
     * 상품이 한 그룹), 그룹·웨이브마다 다른 전략을 고르면 한 후보 풀을 두 정의가 다른 순서로
     * 소진하게 되어 정렬도 배분도 성립하지 않는다.
     */
    private AllocExecuteResponse allocate(List<OutbLine> lines, List<String> wavNos) {
        List<Long> lineIds = lines.stream().map(OutbLine::getId).toList();
        Map<Long, Long> alreadyByLine = outbAllocRepository.sumAlocQtyByLineIds(lineIds);
        Map<String, OutbAlloc> existingAllocs = existingAllocMap(lineIds);

        // findTargetLines가 prod_id ASC로 정렬해 주므로 삽입 순서를 지키는 맵이면 그룹 순회도 prod_id ASC다.
        // 그룹 순서를 고정하는 것이 그룹 간 데드락을 막는다(§ 락 순서) — 전략이 건드릴 수 없는 축이다.
        Map<Long, List<OutbLine>> byProd = new LinkedHashMap<>();
        for (OutbLine line : lines) {
            byProd.computeIfAbsent(line.getProd().getId(), key -> new ArrayList<>()).add(line);
        }

        AlocStgy stgy = alocStgyService.select(
                lines.stream().map(line -> target(line, alreadyByLine)).toList()).orElse(null);
        AlocStgyDefinition def = stgy != null ? AlocStgyResponse.from(stgy).toDefinition() : null;
        Long stgyId = stgy != null ? stgy.getId() : null;
        Long rvsnNo = stgy != null ? stgy.getLastRvsnNo() : null;

        // 존 업무유형은 계층 지정 판정에만 쓰인다. 후보를 락을 걸며 한 건씩 읽는 구조라
        // 재고 조회에 존을 조인할 수 없어, 마스터를 통째로 읽어 메모리에서 붙인다.
        Map<Long, String> bizDvsnByZon = alocQueryRepository.bizDvsnByZon();

        List<AllocExecuteResponse.LineResult> results = new ArrayList<>();
        List<AlocDecisionTrace> groupTraces = new ArrayList<>();
        Set<Long> touchedWaves = new HashSet<>();
        Set<OutbOrder> touchedOrders = new LinkedHashSet<>();
        long totalReq = 0;
        long totalAloc = 0;

        for (Map.Entry<Long, List<OutbLine>> group : byProd.entrySet()) {
            List<Inv> locked = lockCandidates(group.getKey());
            Map<Long, Inv> lockedById = new LinkedHashMap<>();
            locked.forEach(inv -> lockedById.put(inv.getId(), inv));

            List<AlocInvnCandidate> candidates = locked.stream()
                    .map(inv -> AlocInvnCandidate.of(inv, bizDvsnOf(bizDvsnByZon, inv)))
                    .toList();

            Map<Long, OutbLine> lineById = new LinkedHashMap<>();
            group.getValue().forEach(line -> lineById.put(line.getId(), line));
            List<AlocLineTarget> targets = group.getValue().stream()
                    .map(line -> target(line, alreadyByLine)).toList();

            AlocGroupPlan plan = AlocPlanner.plan(def, group.getKey(),
                    group.getValue().get(0).getProd().getProdCd(), targets, candidates);
            groupTraces.add(plan.trace());

            for (AlocGroupPlan.LinePlan linePlan : plan.lines()) {
                OutbLine line = lineById.get(linePlan.outbLineId());
                for (AlocGroupPlan.Assignment assignment : linePlan.assignments()) {
                    reserve(line, lockedById.get(assignment.invId()), assignment.qty(),
                            existingAllocs, stgyId, rvsnNo);
                }
                if (linePlan.asgnQty() > 0) {
                    touchedOrders.add(line.getOutbOrder());
                }
                OutbWave wave = line.getOutbOrder().getWave();
                if (wave != null) {
                    touchedWaves.add(wave.getId());
                }
                totalReq += linePlan.reqQty();
                totalAloc += linePlan.asgnQty();
                results.add(toLineResult(linePlan));
            }
        }

        // 주문 상태 재산출 — 라인마다가 아니라 주문마다 한 번
        touchedOrders.forEach(outbAllocRepository::recalcStatus);

        if (stgy != null) {
            stgyExecLogService.log(StgyTyp.ALOC, stgyId, rvsnNo, TrgrTyp.MANUAL, tgtRef(wavNos),
                    "라인 " + results.size() + "건 · 요청 " + totalReq + " 중 할당 " + totalAloc,
                    Map.of("groups", groupTraces));
        }

        return new AllocExecuteResponse(touchedWaves.size(), results.size(),
                totalReq, totalAloc, totalReq - totalAloc,
                stgyId, stgy != null ? stgy.getStgyNm() : null, rvsnNo, results);
    }

    /**
     * 실행 로그의 대상 참조. 컬럼이 30자라 웨이브를 여러 개 고르면 다 담기지 않는다 —
     * 잘린 번호를 남기느니 「외 N건」으로 줄인다. 전체 목록은 trace가 아니라 응답에 있다.
     */
    private static String tgtRef(List<String> wavNos) {
        String joined = String.join(",", wavNos);
        if (joined.length() <= 30) {
            return joined;
        }
        return wavNos.get(0) + " 외 " + (wavNos.size() - 1) + "건";
    }

    /**
     * 존 미등록 로케이션(zon_id가 가리키는 존이 없음)은 업무유형이 없다 — 계층 지정 조건에서 자연히 빠진다.
     * 존은 id로만 본다 — 지연 프록시의 id는 DB를 안 타고, 존이 없어도 프록시는 null이 아니라 초기화 때 터지므로
     * 코드를 꺼내지 않는다.
     */
    private static String bizDvsnOf(Map<Long, String> bizDvsnByZon, Inv inv) {
        return bizDvsnByZon.get(inv.getLoc().getZon().getId());
    }

    private AlocLineTarget target(OutbLine line, Map<Long, Long> alreadyByLine) {
        return AlocLineTarget.of(line, alreadyByLine.getOrDefault(line.getId(), 0L));
    }

    private static AllocExecuteResponse.LineResult toLineResult(AlocGroupPlan.LinePlan plan) {
        List<AllocExecuteResponse.Assignment> assignments = plan.assignments().stream()
                .map(a -> new AllocExecuteResponse.Assignment(a.invId(), a.locCd(), a.lotNo(), a.qty()))
                .toList();
        List<AllocExecuteResponse.Skip> skips = plan.skips().stream()
                .map(s -> new AllocExecuteResponse.Skip(s.invId(), s.locCd(), s.lotNo(), s.reason()))
                .toList();
        return new AllocExecuteResponse.LineResult(plan.outbLineId(), plan.outbNo(), plan.prodCd(),
                plan.reqQty(), plan.asgnQty(), plan.shortQty(), assignments, skips);
    }

    /**
     * 후보 전체를 잠근다 — 순서는 InvStore가 재고 키 오름차순으로 정한다.
     *
     * <p>정렬 순서(전략이 정한다)와 락 순서(고정)를 분리하는 것이 이 메서드의 전부다. 후보가
     * 겹치는 두 실행이 각자 정렬 순으로 잠그면 서로 반대 순서가 되어 데드락이 난다 —
     * 관리자가 정렬을 바꿔도 락 순서는 언제나 같다. 그룹 순회가 prod 오름차순이므로
     * ({@link #allocate} 참고) 실행 전체의 락 순서도 전역 키 오름차순이 된다.
     *
     * <p>필요한 만큼이 아니라 <b>후보 전체</b>를 잠그는 이유: 필요분만 잠그면 락 후
     * 가용이 줄었을 때 안 잠근 후보가 뒤늦게 필요해지고, 그때 순서 밖의 행을 추가로 잡게 된다.
     *
     * <p>락을 잡는 사이에 사라진 재고(다른 트랜잭션이 0으로 만들어 행 삭제)는 후보에서 빠진다.
     */
    private List<Inv> lockCandidates(Long prodId) {
        return List.copyOf(invStore.lockAllByIds(outbAllocRepository.findCandidateIds(prodId)).values());
    }

    /** 재고 예약 + 할당 레코드 기록. 물리 이동이 아니므로 inv_hist에는 아무것도 남기지 않는다 */
    private void reserve(OutbLine line, Inv candidate, long qty, Map<String, OutbAlloc> existingAllocs,
                         Long alocStgyId, Long rvsnNo) {
        invStore.reserve(candidate, qty);
        String key = allocKey(line.getId(), candidate.getId());
        OutbAlloc existing = existingAllocs.get(key);
        if (existing != null) {
            // 전략 컬럼은 처음 값을 유지한다 — 이미 기록된 수량의 근거를 나중 실행이 바꾸지 않는다
            existing.addQty(qty);
            return;
        }
        OutbAlloc created = outbAllocRepository.save(OutbAlloc.builder()
                .outbLine(line).inv(candidate).alocQty(qty)
                .alocStgyId(alocStgyId).rvsnNo(rvsnNo).build());
        existingAllocs.put(key, created);
    }

    // ── 수동할당 ──────────────────────────────────────────────────────────────

    /**
     * 수동할당 — 사용자가 라인 ↔ 재고를 직접 지정한다. 저장 경로(락 · 예약 · 할당 기록)는
     * 자동할당과 같고 다른 것은 둘뿐이다: 후보를 사람이 고른다는 것, 그리고
     * <b>잔여수명 필터가 차단이 아니라 경고</b>라는 것(§ 수동할당).
     *
     * <p><b>전략을 타지 않는다.</b> 후보를 사람이 고르는 업무라 정렬·배분이 의미를 갖지 않는다 —
     * {@code aloc_stgy_id}가 NULL로 남아 화면에서 전략 실행분과 구분된다.
     *
     * <p>검증은 <b>요청의 전 행</b>에 대해 먼저 수행한다. 첫 행만 보고 통과시키면 나머지 행의
     * 과할당·가용초과가 DB 제약까지 내려가고, 그때는 어느 행이 문제인지 알려줄 수 없다.
     *
     * <p>응답 타입은 자동할당과 같지만 의미가 하나 다르다 — <b>수동할당은 요청한 만큼만 붙이므로
     * {@code reqQty == alocQty} 이고 {@code shortQty} 는 항상 0</b>이다. 모자라면 실패로 끝나지
     * 부분 성공하지 않기 때문이다. 자동할당의 {@code shortQty}(재고 부족으로 못 채운 잔량)와
     * 같은 칸이지만 같은 뜻이 아니다. {@code waveCount} 도 요청 경로상 항상 1이다.
     */
    @Transactional
    public AllocExecuteResponse allocateManual(Long wavId, ManualAllocRequest request) {
        List<ManualAllocRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("할당할 대상이 없습니다.");
        }
        // 웨이브 행 락 — 라인 잔여 검증(②)이 재고 락보다 먼저라, 자동할당과 같은 이유로 잠근다.
        // 상태는 보지 않는다(자동할당과 같다 — 발행된 웨이브도 잔량이 남으면 대상이다)
        lockWave(wavId);

        // ① 라인·재고를 모으고 요청 자체의 형식을 본다
        Map<Long, Long> reqByLine = new LinkedHashMap<>();
        Map<Long, Long> reqByInv = new LinkedHashMap<>();
        for (ManualAllocRequest.Item item : items) {
            if (item.getOutbLineId() == null || item.getInvId() == null) {
                throw new IllegalArgumentException("할당할 라인과 재고를 모두 지정하세요.");
            }
            if (item.getQty() == null || item.getQty() < 1) {
                throw new IllegalArgumentException("할당수량은 1 이상이어야 합니다.");
            }
            reqByLine.merge(item.getOutbLineId(), item.getQty(), Long::sum);
            reqByInv.merge(item.getInvId(), item.getQty(), Long::sum);
        }

        // ② 라인이 이 웨이브의 것인지 + 라인별 합계가 잔여요청을 넘지 않는지 (전 행 기준)
        Map<Long, OutbLine> lines = new LinkedHashMap<>();
        Map<Long, Long> alreadyByLine = outbAllocRepository.sumAlocQtyByLineIds(List.copyOf(reqByLine.keySet()));
        for (Map.Entry<Long, Long> entry : reqByLine.entrySet()) {
            OutbLine line = findLine(entry.getKey());
            OutbWave wave = line.getOutbOrder().getWave();
            if (wave == null || !wave.getId().equals(wavId)) {
                throw new IllegalArgumentException("이 웨이브에 편성된 주문의 라인이 아닙니다: " + line.getOutbOrder().getOutbNo());
            }
            // 자동할당은 findTargetLines가 같은 주문을 조용히 걸러낸다 — 여기서는 사람이 고른
            // 라인이라 이유를 말해 준다 (recalcStatus의 SHIPPED 예외와 한 쌍)
            if (line.getOutbOrder().getStatus() == OutbStatus.SHIPPED) {
                throw new IllegalArgumentException("출고확정된 주문에는 할당할 수 없습니다: "
                        + line.getOutbOrder().getOutbNo());
            }
            long remain = line.getOdrQty() - alreadyByLine.getOrDefault(line.getId(), 0L);
            if (entry.getValue() > remain) {
                throw new IllegalArgumentException("주문수량을 초과했습니다 (잔여 " + Math.max(remain, 0)
                        + ", 요청 " + entry.getValue() + "): " + line.getOutbOrder().getOutbNo()
                        + " / " + line.getProd().getProdCd());
            }
            lines.put(line.getId(), line);
        }

        // ③ 재고를 잠근다 — InvStore가 키 오름차순으로 잠가 자동할당·할당해제와 순서가 같다
        //    (섞여도 데드락이 없다). id 정렬로는 안 된다 — 자동할당은 prod을 먼저 돌아 다상품에서 어긋난다
        Map<Long, Inv> locked = invStore.lockAllByIds(reqByInv.keySet());
        for (Long invId : reqByInv.keySet()) {
            if (!locked.containsKey(invId)) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + invId);
            }
        }

        // ④ 재고별 합계가 가용재고를 넘지 않는지 + 보관 재고인지 (역시 전 행 기준)
        for (Map.Entry<Long, Long> entry : reqByInv.entrySet()) {
            Inv candidate = locked.get(entry.getKey());
            if (candidate.getLoc().getLocTyp() != LocTyp.STORAGE) {
                throw new IllegalArgumentException("보관 로케이션의 재고만 할당할 수 있습니다: "
                        + candidate.getLoc().getLocCd());
            }
            if (entry.getValue() > candidate.avalQty()) {
                throw new IllegalArgumentException("가용재고를 초과했습니다 (가용 " + candidate.avalQty()
                        + ", 요청 " + entry.getValue() + "): " + candidate.getProd().getProdCd()
                        + " @ " + candidate.getLoc().getLocCd());
            }
        }

        // ⑤ 상품 일치 · 기한 경과 Lot 차단은 행 단위로 본다 (라인과 재고의 조합에 걸리는 규칙)
        Map<String, OutbAlloc> existingAllocs = existingAllocMap(List.copyOf(lines.keySet()));
        List<AllocExecuteResponse.LineResult> results = new ArrayList<>();
        Map<Long, List<AllocExecuteResponse.Assignment>> assignedByLine = new LinkedHashMap<>();

        for (ManualAllocRequest.Item item : items) {
            OutbLine line = lines.get(item.getOutbLineId());
            Inv candidate = locked.get(item.getInvId());
            if (!candidate.getProd().getId().equals(line.getProd().getId())) {
                throw new IllegalArgumentException("라인의 상품과 다른 재고입니다: "
                        + candidate.getProd().getProdCd() + " ≠ " + line.getProd().getProdCd());
            }
            if (AlocPlanner.expired(candidate.getLot().getExpiryDt(), line.getOutbOrder().getExpctDe())) {
                throw new IllegalArgumentException("유통기한이 지난 Lot은 할당할 수 없습니다: "
                        + candidate.getLot().getLotNo());
            }
            reserve(line, candidate, item.getQty(), existingAllocs, null, null);
            assignedByLine.computeIfAbsent(line.getId(), key -> new ArrayList<>())
                    .add(new AllocExecuteResponse.Assignment(candidate.getId(),
                            candidate.getLoc().getLocCd(), candidate.getLot().getLotNo(), item.getQty()));
        }

        long totalReq = 0;
        Set<OutbOrder> touchedOrders = new LinkedHashSet<>();
        for (OutbLine line : lines.values()) {
            long qty = reqByLine.get(line.getId());
            totalReq += qty;
            touchedOrders.add(line.getOutbOrder());
            results.add(new AllocExecuteResponse.LineResult(line.getId(),
                    line.getOutbOrder().getOutbNo(), line.getProd().getProdCd(),
                    qty, qty, 0, assignedByLine.getOrDefault(line.getId(), List.of()), List.of()));
        }
        touchedOrders.forEach(outbAllocRepository::recalcStatus);
        return AllocExecuteResponse.of(1, results.size(), totalReq, totalReq, results);
    }

    // ── 할당해제 ──────────────────────────────────────────────────────────────

    /**
     * 할당해제 — {@code outb_alloc} 삭제 + {@code inv.aloc_qty} 복원. 물리 이동 전이라 이력은 없다.
     *
     * <p>주문에 할당이 한 건도 남지 않으면 {@code ALLOCATED → CREATED}로 되돌린다.
     * 그게 없으면 상태는 ALLOCATED인데 할당이 0건인 주문이 남아 확정취소도 웨이브 빼기도
     * 영영 열리지 않는다.
     *
     * <p><b>피킹지시가 발행된 할당은 실적이 0이어도 해제할 수 없다</b> — 지시 행(pikng_task)이
     * 할당과 1:1이라, 해제하면 지시가 삭제된 할당을 가리키는 미아가 된다. 지시취소가 먼저다.
     * 판정은 <b>할당 단위</b>다(웨이브 단위가 아니다) — 같은 웨이브의 다른 할당에 지시가 있어도
     * 이 할당의 지시가 취소됐다면 해제할 수 있다.
     */
    @Transactional
    public void release(AllocReleaseRequest request) {
        List<Long> allocIds = distinct(request.getAllocIds());
        if (allocIds.isEmpty()) {
            throw new IllegalArgumentException("해제할 할당을 선택하세요.");
        }
        List<OutbAlloc> allocs = outbAllocRepository.findAllWithLineByIds(allocIds);
        if (allocs.size() != allocIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 할당이 포함돼 있습니다.");
        }
        for (OutbAlloc alloc : allocs) {
            if (!alloc.releasable()) {
                throw new IllegalArgumentException("피킹이 시작된 할당은 해제할 수 없습니다 (피킹 "
                        + alloc.getPikngQty() + "): " + alloc.getOutbLine().getOutbOrder().getOutbNo());
            }
        }

        // 웨이브 행 락 — 지시 발행(pikng_task 생성)과의 직렬화. 락 없이 발행과 해제가 겹치면
        // 발행이 방금 지워진 할당의 지시를 만든다. 순서는 웨이브(오름차순) → 재고 한 방향이다
        allocs.stream()
                .map(alloc -> waveOf(alloc).getId())
                .distinct().sorted()
                .forEach(this::lockWave);

        // 지시 발행 가드 — 웨이브 락 뒤에 조회해야 발행 중인 트랜잭션과 어긋나지 않는다
        List<PikngTask> issued = pikngTaskRepository
                .findByOutbAllocIdInAndStatusNot(allocIds, PikngTaskStatus.CANCELLED);
        if (!issued.isEmpty()) {
            throw new IllegalArgumentException("피킹지시가 발행된 할당은 해제할 수 없습니다"
                    + " (그 지시의 취소가 먼저입니다): "
                    + issued.get(0).getOutbAlloc().getOutbLine().getOutbOrder().getOutbNo());
        }

        // 예약 복원도 할당과 같은 창구로 잠근다 — 해제와 할당이 동시에 돌아도 순서가 하나다
        Map<Long, List<OutbAlloc>> byInv = new LinkedHashMap<>();
        for (OutbAlloc alloc : allocs) {
            byInv.computeIfAbsent(alloc.getInv().getId(), key -> new ArrayList<>()).add(alloc);
        }
        Map<Long, Inv> locked = invStore.lockAllByIds(byInv.keySet());
        for (Map.Entry<Long, List<OutbAlloc>> entry : byInv.entrySet()) {
            Inv target = locked.get(entry.getKey());
            if (target == null) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + entry.getKey());
            }
            for (OutbAlloc alloc : entry.getValue()) {
                invStore.release(target, alloc.getAlocQty());
            }
        }

        Set<OutbOrder> orders = new HashSet<>();
        allocs.forEach(alloc -> orders.add(alloc.getOutbLine().getOutbOrder()));
        outbAllocRepository.deleteAll(allocs);
        // 남은 건수를 세기 전에 삭제를 반영한다 — 안 하면 방금 지운 행까지 세어 복귀가 일어나지 않는다
        outbAllocRepository.flush();

        // 남은 할당이 없으면 CREATED로, 남은 것이 전부 소진됐으면 PICKED로 — 「0건이 됐나」만
        // 묻던 옛 판정이 실적 있는 할당 하나만 남은 주문을 PICKING에 고이게 했다
        orders.forEach(outbAllocRepository::recalcStatus);
    }

    /**
     * 할당이 속한 웨이브. 미편성 주문의 할당은 도달 불가지만(할당 대상 조회가 웨이브에서 출발한다),
     * null을 조용히 건너뛰면 그 불변식이 깨진 날 아무 신호 없이 락이 사라진다.
     */
    private static OutbWave waveOf(OutbAlloc alloc) {
        OutbOrder order = alloc.getOutbLine().getOutbOrder();
        if (order.getWave() == null) {
            throw new IllegalStateException("웨이브에 편성되지 않은 주문의 할당입니다 (정합성 오류): "
                    + order.getOutbNo());
        }
        return order.getWave();
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    /**
     * 합산 대상이 되는 기존 할당 — (라인, 재고) 키 맵. 같은 조합이 다시 할당되면 새 행 대신
     * 여기에 합산해 화면과 해제 단위가 불필요하게 쪼개지지 않게 한다.
     *
     * <p><b>살아 있는 지시가 붙은 할당은 빼고 돌려준다.</b> 거기에 합산하면 {@code aloc_qty}만
     * 커지고 지시의 {@code drct_qty}는 그대로라 항등식이 조용히 깨지며(DB 제약이 없는 축이라
     * 아무도 알려주지 않는다), 그 할당에 새 지시를 만들려 하면 {@code uq_pikng_task_alloc}에
     * 걸린다. 빠진 조합은 새 행이 되어 「재할당은 행이 하나 더 생기는 것으로 누적된다」가 그대로
     * 성립하고, 결품 종결로 닫힌 할당의 사유·수량도 덮이지 않고 남는다.
     *
     * <p>판정 축은 웨이브 상태가 아니라 <b>할당 단위</b>다(할당해제와 같다) — 취소된 지시만 붙은
     * 할당은 합산해도 안전하다. 부분 유니크가 CANCELLED를 세지 않아 새 지시를 만들 수 있고,
     * 그 지시의 {@code drct_qty}는 합산 후 {@code aloc_qty}와 같다.
     */
    private Map<String, OutbAlloc> existingAllocMap(List<Long> lineIds) {
        Map<String, OutbAlloc> map = new HashMap<>();
        if (lineIds.isEmpty()) {
            return map;
        }
        Set<Long> issued = new HashSet<>(pikngTaskRepository
                .findLiveAllocIdsByLineIds(lineIds, PikngTaskStatus.CANCELLED));
        for (OutbAlloc alloc : outbAllocRepository.findByOutbLineIdIn(lineIds)) {
            if (issued.contains(alloc.getId())) {
                continue;
            }
            map.put(allocKey(alloc.getOutbLine().getId(), alloc.getInv().getId()), alloc);
        }
        return map;
    }

    private static String allocKey(Long outbLineId, Long invId) {
        return outbLineId + ":" + invId;
    }

    private OutbWave findWave(Long wavId) {
        return outbWaveRepository.findById(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }

    /** 실행 단위 직렬화용 웨이브 행 락. 락 순서는 웨이브(오름차순) → 재고(재고 키 오름차순) 한 방향이다 */
    private OutbWave lockWave(Long wavId) {
        return outbWaveRepository.findByIdForUpdate(wavId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 웨이브입니다: " + wavId));
    }

    private OutbLine findLine(Long outbLineId) {
        return outbLineRepository.findById(outbLineId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 출고 라인입니다: " + outbLineId));
    }

    private static List<Long> distinct(List<Long> ids) {
        return ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct()
                        .sorted(Comparator.naturalOrder()).toList();
    }
}
