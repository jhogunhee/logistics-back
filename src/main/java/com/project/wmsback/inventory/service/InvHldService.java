package com.project.wmsback.inventory.service;

import com.project.wmsback.inventory.dto.InvHldAcrstResponse;
import com.project.wmsback.inventory.dto.InvHldAcrstSearchCond;
import com.project.wmsback.inventory.dto.InvHldRegisterRequest;
import com.project.wmsback.inventory.dto.InvHldReleaseRequest;
import com.project.wmsback.inventory.dto.InvHldResponse;
import com.project.wmsback.inventory.dto.InvHldSearchCond;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvHld;
import com.project.wmsback.inventory.entity.InvHldAcrst;
import com.project.wmsback.inventory.entity.InvHldRlzAcrst;
import com.project.wmsback.inventory.entity.InvHldStatus;
import com.project.wmsback.inventory.repository.InvHldAcrstRepository;
import com.project.wmsback.inventory.repository.InvHldRepository;
import com.project.wmsback.inventory.repository.InvHldRlzAcrstRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.nbr.service.NbrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 재고 보류 (수량 방식 — 등록이 inv.hld_qty를 늘려 가용재고에서 빼고, 해제가 되돌린다).
 *
 * 보류/해제는 물리 이동이 아니므로 inv_hist에 기록하지 않는다 (할당·이동지시 예약과 같은 판단).
 * onHand 불변. 보류의 원장은 전용 실적 2테이블(inv_hld_acrst / inv_hld_rlz_acrst)이다 —
 * 「물리 변동 실적 테이블 없음」 원칙의 유일한 예외 (등록·해제 사유 히스토리 보존 요구).
 * 예약과 보류는 배타: 보류는 가용재고(onHand − aloc − hld)에서만 잡는다 (ck_inv_qty가 최후 방어).
 * 같은 재고 행에는 사유가 같든 다르든 보류가 여러 건 병존한다 (합산도 차단도 하지 않는 이유는
 * docs/design.md 「재고 보류」 참고 — 중복 등록 실수는 해제(사유: 오등록)로 되돌린다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvHldService {

    private static final String HLD_NO_RULE_CD = "HLD_NO";
    private static final String HLD_RSN_GRP_CD = "HLD_RSN";
    private static final String HLD_RLZ_RSN_GRP_CD = "HLD_RLZ_RSN";

    private final InvStore invStore;
    private final InvHldRepository invHldRepository;
    private final InvHldAcrstRepository invHldAcrstRepository;
    private final InvHldRlzAcrstRepository invHldRlzAcrstRepository;
    private final RsnValidator rsnValidator;
    private final NbrService nbrService;

    public List<InvHldResponse> list(InvHldSearchCond cond) {
        return invHldRepository.search(cond);
    }

    public List<InvHldAcrstResponse> listAcrst(InvHldAcrstSearchCond cond) {
        return invHldAcrstRepository.search(cond);
    }

    public List<InvHldAcrstResponse> listRlzAcrst(InvHldAcrstSearchCond cond) {
        return invHldRlzAcrstRepository.search(cond);
    }

    /**
     * 보류 등록 (등록 즉시 발효). 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     *
     * 재고 행을 전부 선락(InvStore가 키 오름차순으로 잠근다)한 뒤 건별 처리로 들어간다.
     * 건별로 「재고 락 → 채번」을 반복하면 채번 카운터 행 락(커밋까지 유지)이 재고 행 락 사이에
     * 끼는데, 같은 날짜의 카운터는 모든 등록이 한 행을 공유하므로 재고가 겹치는 두 요청이
     * 카운터와 재고를 나눠 쥐고 맞물린다. 채번은 재고 락이 모두 잡힌 뒤에만 일어난다.
     *
     * @return 발급된 보류 번호 목록 (요청 순서)
     */
    @Transactional
    public List<String> register(InvHldRegisterRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("보류 대상이 없습니다.");
        }
        Set<Long> invIds = new LinkedHashSet<>();
        for (InvHldRegisterRequest.Item item : request.getItems()) {
            if (item.getInvId() == null) {
                throw new IllegalArgumentException("보류할 재고가 지정되지 않았습니다.");
            }
            invIds.add(item.getInvId());
        }

        // 재고 행 선락 — 보류(hld) 증감의 직렬화 지점 (예약이 같은 행을 잡는 지점과 동일)
        Map<Long, Inv> locked = invStore.lockAllByIds(invIds);

        List<String> hldNos = new ArrayList<>();
        for (InvHldRegisterRequest.Item item : request.getItems()) {
            Inv inv = locked.get(item.getInvId());
            if (inv == null) {
                throw new IllegalArgumentException("존재하지 않는 재고입니다: " + item.getInvId());
            }
            hldNos.add(registerOne(item, inv));
        }
        return hldNos;
    }

    private String registerOne(InvHldRegisterRequest.Item item, Inv inv) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("보류수량은 1 이상이어야 합니다.");
        }
        return holdOn(inv, item.getQty(), item.getRsnCd(), item.getRsnDscr());
    }

    /**
     * 재고 행 하나에 보류 한 건 — 검증 · hld 증가 · 보류 건 · 등록 실적. 화면 등록과 반품 검수(불량분)가 같이 쓴다.
     * 호출자가 그 재고 행의 락을 이미 잡고 있어야 하고, 채번(HLD_NO)이 여기서 일어나므로
     * 잡을 재고 락이 더 남았을 때 부르면 안 된다 (락 순서 — 채번은 재고 락을 전부 잡은 뒤).
     */
    @Transactional
    public String holdOn(Inv inv, long qty, String rsnCd, String rsnDscr) {
        if (qty < 1) {
            throw new IllegalArgumentException("보류수량은 1 이상이어야 합니다.");
        }
        String dscr = rsnValidator.validate(HLD_RSN_GRP_CD, "보류사유", rsnCd, rsnDscr);

        Prod prodEntity = inv.getProd();
        Lot lotEntity = inv.getLot();
        Loc locEntity = inv.getLoc();

        // v1 보류 대상은 보관 재고만 — 스테이징까지 허용하면 적치·출고확정의 수량 체크에도 파급이 생긴다
        if (locEntity.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 보류할 수 있습니다: " + locEntity.getLocCd());
        }
        // 예약과 보류는 배타 — 보류는 가용재고에서만 잡는다 (예약 잔량이 있어도 남은 가용분은 보류 가능)
        if (qty > inv.avalQty()) {
            throw new IllegalArgumentException("보류수량이 가용재고를 초과했습니다 (가용 " + inv.avalQty() + "): "
                    + prodEntity.getProdCd() + " @ " + locEntity.getLocCd());
        }

        invStore.hold(inv, qty);
        InvHld hld = InvHld.builder()
                .hldNo(nbrService.issue(HLD_NO_RULE_CD, LocalDate.now()))
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(qty)
                .rsnCd(rsnCd).rsnDscr(dscr)
                .build();
        invHldRepository.save(hld);
        // 실적은 자기완결 로그 — 건이 갱신돼도 등록 시점 기록이 보존된다
        invHldAcrstRepository.save(InvHldAcrst.builder()
                .hldNo(hld.getHldNo())
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(qty)
                .rsnCd(rsnCd).rsnDscr(dscr)
                .build());
        return hld.getHldNo();
    }

    /**
     * 보류 해제 (보류 건을 지목해 잔량 이내로, 건마다 부분 해제 허용).
     * 오등록 취소도 이 경로다 — 별도 취소 상태 없이 해제(사유: 오등록)로 흡수한다.
     * 전체가 한 트랜잭션 — 한 건이라도 검증에 걸리면 전량 롤백.
     *
     * 락은 재고 행을 전부 잡은 뒤 보류 건을 잡는다. 건별로 「보류 건 → 그 건의 재고 행」 순서로
     * 잡으면 다건에서 교착이 난다 — 한 재고 행에 보류가 여러 건 병존할 수 있어서,
     * 그 행의 보류 둘을 함께 해제하는 요청이 앞 건에서 재고 행을 쥔 채 뒤 건의 보류 건을 기다리는
     * 동안, 그 보류 건 하나만 해제하는 요청이 반대로 물린다. 그래서 재고 행을 먼저 모두
     * 선락하고(InvStore가 키 오름차순으로 잠근다), 보류 건은 id 오름차순으로 잡아 순서를 맞춘다.
     */
    @Transactional
    public void release(InvHldReleaseRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("해제 대상이 없습니다.");
        }
        Set<Long> hldIds = new LinkedHashSet<>();
        for (InvHldReleaseRequest.Item item : request.getItems()) {
            Long hldId = item.getHldId();
            if (hldId == null) {
                throw new IllegalArgumentException("해제할 보류 건이 지정되지 않았습니다.");
            }
            // 같은 건을 두 번 실으면 잔량을 두 번 깎으면서 실적만 두 줄 남는다 — 애초에 거부한다
            if (!hldIds.add(hldId)) {
                throw new IllegalArgumentException("같은 보류 건이 두 번 실렸습니다 — 한 번에 한 값으로만 해제할 수 있습니다: " + hldId);
            }
        }

        // 잠글 재고 행을 고르기 위한 사전 조회. 정렬 키(상품·로케이션·Lot)는 보류 건이 만들어질 때
        // 정해져 바뀌지 않으므로 락 없이 미리 읽는다
        Map<Long, InvKey> keyByHldId = new HashMap<>();
        for (InvLockKey row : invHldRepository.findLockKeysByIdIn(hldIds)) {
            keyByHldId.put(row.id(), row.key());
        }
        for (Long hldId : hldIds) {
            if (!keyByHldId.containsKey(hldId)) {
                throw new IllegalArgumentException("존재하지 않는 보류 건입니다: " + hldId);
            }
        }

        // 없는 행을 여기서 문제 삼지 않는다 — 전량 해제된 건은 재고 행이 남아 있지 않을 수 있고,
        // 그건 아래 상태 검증이 「보류중인 건만 해제할 수 있다」로 잡아야 할 몫이다
        Map<InvKey, Inv> locked = invStore.lockAll(keyByHldId.values());

        request.getItems().stream()
                .sorted(Comparator.comparing(InvHldReleaseRequest.Item::getHldId))
                .forEach(item -> releaseOne(item, locked));
    }

    private void releaseOne(InvHldReleaseRequest.Item item, Map<InvKey, Inv> locked) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("해제수량은 1 이상이어야 합니다.");
        }
        String rsnDscr = rsnValidator.validate(HLD_RLZ_RSN_GRP_CD, "해제사유", item.getRsnCd(), item.getRsnDscr());

        InvHld hld = invHldRepository.findByIdForUpdate(item.getHldId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보류 건입니다: " + item.getHldId()));
        if (hld.getStatus() != InvHldStatus.HELD) {
            throw new IllegalArgumentException("보류중 상태의 건만 해제할 수 있습니다 (현재 " + hld.getStatus().getLabel() + "): " + hld.getHldNo());
        }
        if (item.getQty() > hld.remainingQty()) {
            throw new IllegalArgumentException("해제수량이 미해제 잔량을 초과했습니다 (잔량 " + hld.remainingQty() + "): " + hld.getHldNo());
        }

        // 보류 잔량이 있는 한 inv 행은 삭제되지 않으므로(ck_inv_qty: hld <= onHand → onHand > 0) 없으면 정합성 오류다.
        // 선락 단계에서 잠근 행을 꺼내 쓴다 (보류 건의 재고 키는 생성 후 바뀌지 않는다)
        Inv inv = locked.get(new InvKey(hld.getProd().getId(), hld.getLoc().getId(), hld.getLot().getId()));
        if (inv == null) {
            throw new IllegalStateException("보류 건이 잡아둔 재고가 없습니다 (정합성 오류): " + hld.getHldNo());
        }
        if (inv.getHldQty() < item.getQty()) {
            throw new IllegalStateException("보류 잔량보다 재고의 보류 수량이 적습니다 (정합성 오류 — 보류 " + inv.getHldQty()
                    + " / 해제 " + item.getQty() + "): " + hld.getHldNo());
        }

        hld.release(item.getQty());
        invStore.releaseHold(inv, item.getQty());
        invHldRlzAcrstRepository.save(InvHldRlzAcrst.builder()
                .hldNo(hld.getHldNo())
                .prod(hld.getProd()).loc(hld.getLoc()).lot(hld.getLot())
                .rlzQty(item.getQty())
                .rsnCd(item.getRsnCd()).rsnDscr(rsnDscr)
                .build());
    }
}
