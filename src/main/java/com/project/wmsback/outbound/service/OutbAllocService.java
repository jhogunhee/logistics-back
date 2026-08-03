    package com.project.wmsback.outbound.service;

import com.project.mdm.store.entity.Store;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.repository.InvRepository;
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
import com.project.wmsback.outbound.entity.OutbOrder;
import com.project.wmsback.outbound.entity.OutbWave;
import com.project.wmsback.outbound.repository.OutbAllocRepository;
import com.project.wmsback.outbound.repository.OutbLineRepository;
import com.project.wmsback.outbound.repository.OutbWaveRepository;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OutbAllocService {

    private final OutbAllocRepository outbAllocRepository;
    private final OutbWaveRepository outbWaveRepository;
    private final OutbLineRepository outbLineRepository;
    private final InvRepository invRepository;

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
     */
    public List<AllocCandidateResponse> candidates(Long outbLineId) {
        OutbLine line = findLine(outbLineId);
        Store store = line.getOutbOrder().getStore();
        LocalDate baseDe = line.getOutbOrder().getExpctDe();

        List<AllocCandidateResponse> result = new ArrayList<>();
        for (Inv candidate : outbAllocRepository.findCandidates(line.getProd().getId())) {
            if (expired(candidate.getLot(), baseDe)) {
                continue;
            }
            BigDecimal rate = lifeRate(candidate.getLot(), baseDe);
            result.add(new AllocCandidateResponse(
                    candidate.getId(),
                    candidate.getLoc().getId(), candidate.getLoc().getLocCd(),
                    candidate.getLot().getId(), candidate.getLot().getLotNo(),
                    candidate.getLot().getMfgDt(), candidate.getLot().getExpiryDt(),
                    candidate.getOnHandQty(), candidate.avalQty(),
                    rate, lifePass(rate, store)));
        }
        return result;
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
        for (Long wavId : wavIds) {
            // 피킹지시가 발행된(ISSUED) 웨이브에 더 할당하면 지시 없는 할당이 남는다
            findWave(wavId).assertPlanned();
        }

        List<OutbLine> lines = outbAllocRepository.findTargetLines(wavIds);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("할당할 잔량이 남은 라인이 없습니다.");
        }
        return allocate(lines);
    }

    /** 할당 본체. 상품별로 모아 후보를 한 번만 조회·락하고, 그룹 안 라인이 순서대로 소진한다. */
    private AllocExecuteResponse allocate(List<OutbLine> lines) {
        List<Long> lineIds = lines.stream().map(OutbLine::getId).toList();
        // 라인을 처리하며 누적분을 갱신하므로 복사본을 쓴다 — 조회 결과는 빈 입력일 때 불변 맵이다
        Map<Long, Long> alreadyByLine = new HashMap<>(outbAllocRepository.sumAlocQtyByLineIds(lineIds));
        Map<String, OutbAlloc> existingAllocs = existingAllocMap(lineIds);

        // findTargetLines가 prod_id ASC로 정렬해 주므로 삽입 순서를 지키는 맵이면 그룹 순회도 prod_id ASC다.
        // 그룹 순서를 고정하는 것이 그룹 간 데드락을 막는다(§ 락 순서).
        Map<Long, List<OutbLine>> byProd = new LinkedHashMap<>();
        for (OutbLine line : lines) {
            byProd.computeIfAbsent(line.getProd().getId(), k -> new ArrayList<>()).add(line);
        }

        List<AllocExecuteResponse.LineResult> results = new ArrayList<>();
        Set<Long> touchedWaves = new HashSet<>();
        long totalReq = 0;
        long totalAloc = 0;

        for (Map.Entry<Long, List<OutbLine>> group : byProd.entrySet()) {
            List<Inv> fefo = lockCandidates(group.getKey());

            for (OutbLine line : group.getValue()) {
                AllocExecuteResponse.LineResult result =
                        allocateLine(line, fefo, alreadyByLine, existingAllocs);
                results.add(result);
                totalReq += result.reqQty();
                totalAloc += result.alocQty();
                if (result.alocQty() > 0) {
                    line.getOutbOrder().allocate();
                }
                OutbWave wave = line.getOutbOrder().getWave();
                if (wave != null) {
                    touchedWaves.add(wave.getId());
                }
            }
        }
        return new AllocExecuteResponse(touchedWaves.size(), results.size(),
                totalReq, totalAloc, totalReq - totalAloc, results);
    }

    /**
     * 후보를 FEFO 순으로 늘어놓되 <b>락은 id 오름차순으로 한 건씩</b> 건다.
     *
     * <p>FEFO 순서와 id 순서가 다르므로, 후보가 겹치는 두 실행이 각자 FEFO 순으로 잠그면
     * 서로 반대 순서가 되어 데드락이 난다. <b>정렬(FEFO)과 락 순서(id)를 분리</b>하는 것이
     * 이 메서드의 전부다. 검수·속성변경이 「상품 → Lot」 락 순서를 고정한 것과 같은 자리다.
     *
     * <p>{@code WHERE id IN (…) ORDER BY id} 일괄 락으로 바꾸지 말 것 — {@code ORDER BY}는
     * 결과 정렬을 보장할 뿐 <b>락 획득 순서를 보장하지 않는다</b>(플랜에 따라 물리 순서로 잠근다).
     *
     * <p>필요한 만큼이 아니라 <b>후보 전체</b>를 잠그는 것도 같은 이유다. 필요분만 잠그면 락 후
     * 가용이 줄었을 때 안 잠근 후보가 뒤늦게 필요해지고, 그때 더 작은 id를 추가로 잡게 된다.
     */
    private List<Inv> lockCandidates(Long prodId) {
        List<Long> fefoIds = outbAllocRepository.findCandidateIds(prodId);
        Map<Long, Inv> locked = new HashMap<>();
        for (Long invId : fefoIds.stream().sorted().toList()) {
            invRepository.findByIdForUpdate(invId).ifPresent(found -> locked.put(invId, found));
        }
        // 락을 잡는 사이에 사라진 재고(다른 트랜잭션이 0으로 만들어 행 삭제)는 후보에서 빠진다
        return fefoIds.stream().map(locked::get).filter(Objects::nonNull).toList();
    }

    /** 라인 하나를 FEFO 순으로 채운다. 잔여요청이 남으면 그대로 둔다 — 부분할당 허용, 백오더 없음 */
    private AllocExecuteResponse.LineResult allocateLine(OutbLine line, List<Inv> fefo,
                                                        Map<Long, Long> alreadyByLine,
                                                        Map<String, OutbAlloc> existingAllocs) {
        OutbOrder order = line.getOutbOrder();
        Store store = order.getStore();
        LocalDate baseDe = order.getExpctDe();

        long already = alreadyByLine.getOrDefault(line.getId(), 0L);
        long remain = line.getOdrQty() - already;   // 과할당 금지: 잔여요청이 상한이다

        List<AllocExecuteResponse.Assignment> assignments = new ArrayList<>();
        List<AllocExecuteResponse.Skip> skips = new ArrayList<>();
        long reqQty = Math.max(remain, 0);

        for (Inv candidate : fefo) {
            if (remain <= 0) {
                break;
            }
            // 앞 라인이 이미 예약한 만큼 avalQty()가 줄어 있다 — 같은 그룹의 이중 배분이 불가능하다
            long avail = candidate.avalQty();
            if (avail <= 0) {
                continue;
            }
            String skipReason = skipReason(candidate.getLot(), baseDe, store);
            if (skipReason != null) {
                skips.add(new AllocExecuteResponse.Skip(candidate.getId(),
                        candidate.getLoc().getLocCd(), candidate.getLot().getLotNo(), skipReason));
                continue;
            }
            long assign = Math.min(avail, remain);
            reserve(line, candidate, assign, existingAllocs);
            assignments.add(new AllocExecuteResponse.Assignment(candidate.getId(),
                    candidate.getLoc().getLocCd(), candidate.getLot().getLotNo(), assign));
            remain -= assign;
        }

        long assigned = reqQty - Math.max(remain, 0);
        alreadyByLine.put(line.getId(), already + assigned);
        return new AllocExecuteResponse.LineResult(line.getId(), order.getOutbNo(),
                line.getProd().getProdCd(), reqQty, assigned, reqQty - assigned, assignments, skips);
    }

    /** 재고 예약 + 할당 레코드 기록. 물리 이동이 아니므로 inv_hist에는 아무것도 남기지 않는다 */
    private void reserve(OutbLine line, Inv candidate, long qty, Map<String, OutbAlloc> existingAllocs) {
        candidate.reserve(qty);
        String key = allocKey(line.getId(), candidate.getId());
        OutbAlloc existing = existingAllocs.get(key);
        if (existing != null) {
            existing.addQty(qty);
            return;
        }
        OutbAlloc created = outbAllocRepository.save(OutbAlloc.builder()
                .outbLine(line).inv(candidate).alocQty(qty).build());
        existingAllocs.put(key, created);
    }

    // ── 수동할당 ──────────────────────────────────────────────────────────────

    /**
     * 수동할당 — 사용자가 라인 ↔ 재고를 직접 지정한다. 저장 경로(락 · 예약 · 할당 기록)는
     * 자동할당과 같고 다른 것은 둘뿐이다: 후보를 사람이 고른다는 것, 그리고
     * <b>잔여수명 필터가 차단이 아니라 경고</b>라는 것(§ 수동할당).
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
        findWave(wavId).assertPlanned();

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
            long remain = line.getOdrQty() - alreadyByLine.getOrDefault(line.getId(), 0L);
            if (entry.getValue() > remain) {
                throw new IllegalArgumentException("주문수량을 초과했습니다 (잔여 " + Math.max(remain, 0)
                        + ", 요청 " + entry.getValue() + "): " + line.getOutbOrder().getOutbNo()
                        + " / " + line.getProd().getProdCd());
            }
            lines.put(line.getId(), line);
        }

        // ③ 재고를 id 오름차순으로 잠근다 (자동할당과 같은 순서 — 두 경로가 섞여도 데드락이 없다)
        Map<Long, Inv> locked = new LinkedHashMap<>();
        for (Long invId : reqByInv.keySet().stream().sorted().toList()) {
            locked.put(invId, invRepository.findByIdForUpdate(invId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다: " + invId)));
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
            if (expired(candidate.getLot(), line.getOutbOrder().getExpctDe())) {
                throw new IllegalArgumentException("유통기한이 지난 Lot은 할당할 수 없습니다: "
                        + candidate.getLot().getLotNo());
            }
            reserve(line, candidate, item.getQty(), existingAllocs);
            assignedByLine.computeIfAbsent(line.getId(), k -> new ArrayList<>())
                    .add(new AllocExecuteResponse.Assignment(candidate.getId(),
                            candidate.getLoc().getLocCd(), candidate.getLot().getLotNo(), item.getQty()));
        }

        long totalReq = 0;
        for (OutbLine line : lines.values()) {
            long qty = reqByLine.get(line.getId());
            totalReq += qty;
            line.getOutbOrder().allocate();
            results.add(new AllocExecuteResponse.LineResult(line.getId(),
                    line.getOutbOrder().getOutbNo(), line.getProd().getProdCd(),
                    qty, qty, 0, assignedByLine.getOrDefault(line.getId(), List.of()), List.of()));
        }
        return new AllocExecuteResponse(1, results.size(), totalReq, totalReq, 0, results);
    }

    // ── 할당해제 ──────────────────────────────────────────────────────────────

    /**
     * 할당해제 — {@code outb_alloc} 삭제 + {@code inv.aloc_qty} 복원. 물리 이동 전이라 이력은 없다.
     *
     * <p>주문에 할당이 한 건도 남지 않으면 {@code ALLOCATED → CREATED}로 되돌린다.
     * 그게 없으면 상태는 ALLOCATED인데 할당이 0건인 주문이 남아 확정취소도 웨이브 빼기도
     * 영영 열리지 않는다.
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

        // 예약 복원도 자동할당과 같은 락 순서를 쓴다 — 해제와 할당이 동시에 돌아도 순서가 하나다
        Map<Long, List<OutbAlloc>> byInv = new LinkedHashMap<>();
        for (OutbAlloc alloc : allocs) {
            byInv.computeIfAbsent(alloc.getInv().getId(), k -> new ArrayList<>()).add(alloc);
        }
        for (Long invId : byInv.keySet().stream().sorted().toList()) {
            Inv target = invRepository.findByIdForUpdate(invId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다: " + invId));
            for (OutbAlloc alloc : byInv.get(invId)) {
                target.release(alloc.getAlocQty());
            }
        }

        Set<OutbOrder> orders = new HashSet<>();
        allocs.forEach(alloc -> orders.add(alloc.getOutbLine().getOutbOrder()));
        outbAllocRepository.deleteAll(allocs);
        // 남은 건수를 세기 전에 삭제를 반영한다 — 안 하면 방금 지운 행까지 세어 복귀가 일어나지 않는다
        outbAllocRepository.flush();

        for (OutbOrder order : orders) {
            if (outbAllocRepository.countByOutbOrderId(order.getId()) == 0) {
                order.revertToCreated();
            }
        }
    }

    // ── 잔여수명 ──────────────────────────────────────────────────────────────

    /**
     * 후보에서 빠지는 사유. null이면 통과 — 조용히 빠지는 재고를 만들지 않기 위해
     * 사유를 문자열로 돌려주고 호출부가 응답 trace에 담는다.
     */
    private String skipReason(Lot lot, LocalDate baseDe, Store store) {
        if (expired(lot, baseDe)) {
            // 비율과 무관한 하드 가드 — outb_life_rate가 0인 점포에도 기한 지난 Lot을 줄 수는 없다
            return "유통기한 경과 (" + lot.getExpiryDt() + ")";
        }
        BigDecimal rate = lifeRate(lot, baseDe);
        if (rate == null) {
            return null;    // 유통기한 미관리 Lot — 필터 대상이 아니다
        }
        if (lifePass(rate, store)) {
            return null;
        }
        return "잔여수명 " + rate + "% < 점포 기준 " + store.getOutbLifeRate() + "%";
    }

    private boolean expired(Lot lot, LocalDate baseDe) {
        return lot.getExpiryDt() != null && lot.getExpiryDt().isBefore(baseDe);
    }

    /**
     * 잔여수명 비율. <b>분모까지 Lot에서 뽑는다</b>({@code expiry_dt − mfg_dt}).
     *
     * <p>{@code prod.shelf_life_days}(마스터의 현재값)를 분모로 쓰면 두 가지가 깨진다 —
     * ① {@code Lot.expiry_dt}는 생성 시점 스냅샷이라 「마스터 변경에 소급 영향 없음」이 원칙인데
     * 마스터를 고치는 순간 기존 Lot 전체의 비율이 움직이고, ② 벤더가 찍은 유통기한이 계산값과
     * 다른 것이 정상 데이터라 비율이 100%를 넘거나 실제보다 후하게 나온다.
     *
     * <p>입고 검수({@code SHELF_LIFE_PCT})가 {@code shelf_life_days}를 그대로 쓰는 것과 다른데,
     * 그쪽은 Lot이 생성되는 그 시점에 판정하므로 스냅샷과 마스터가 같은 값이기 때문이다.
     *
     * @param baseDe 출고 예정일 — 점포 기준은 「납품 시점의 잔여수명」이다. 할당 실행일로 재면
     *               할당을 며칠 앞당길수록 통과 Lot이 늘어나 기준이 흔들린다.
     * @return 유통기한 미관리 Lot이거나 총수명일수를 산출할 수 없으면 null (필터 대상 아님)
     */
    private BigDecimal lifeRate(Lot lot, LocalDate baseDe) {
        if (lot.getExpiryDt() == null || lot.getMfgDt() == null) {
            return null;
        }
        long totalDays = ChronoUnit.DAYS.between(lot.getMfgDt(), lot.getExpiryDt());
        if (totalDays <= 0) {
            return null;
        }
        long remainDays = ChronoUnit.DAYS.between(baseDe, lot.getExpiryDt());
        return BigDecimal.valueOf(remainDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalDays), 1, RoundingMode.DOWN);
    }

    /** 미관리 Lot(rate == null)은 필터 대상이 아니므로 통과로 본다 */
    private boolean lifePass(BigDecimal rate, Store store) {
        return rate == null || rate.compareTo(BigDecimal.valueOf(store.getOutbLifeRate())) >= 0;
    }

    // ── 공통 ─────────────────────────────────────────────────────────────────

    private Map<String, OutbAlloc> existingAllocMap(List<Long> lineIds) {
        Map<String, OutbAlloc> map = new HashMap<>();
        if (lineIds.isEmpty()) {
            return map;
        }
        for (OutbAlloc alloc : outbAllocRepository.findByOutbLineIdIn(lineIds)) {
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
