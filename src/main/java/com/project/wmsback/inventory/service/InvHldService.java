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
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.mdm.code.entity.CodeDetailId;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import com.project.mdm.prod.entity.Prod;
import com.project.mdm.code.repository.CodeDetailRepository;
import com.project.mdm.nbr.service.NbrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고 보류 (수량 방식 — 등록이 inv.hld_qty를 늘려 가용재고에서 빼고, 해제가 되돌린다).
 *
 * 보류/해제는 물리 이동이 아니므로 inv_hist에 기록하지 않는다 (할당·이동지시 예약과 같은 판단).
 * onHand 불변. 보류의 원장은 전용 실적 2테이블(inv_hld_acrst / inv_hld_rlz_acrst)이다 —
 * 「물리 변동 실적 테이블 없음」 원칙의 유일한 예외 (등록·해제 사유 히스토리 보존 요구).
 * 예약과 보류는 배타: 보류는 가용재고(onHand − aloc − hld)에서만 잡는다 (ck_inv_qty가 최후 방어).
 * docs/design.md 「재고 보류」 참고.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvHldService {

    private static final String HLD_NO_RULE_CD = "HLD_NO";
    private static final String HLD_RSN_GRP_CD = "HLD_RSN";
    private static final String HLD_RLZ_RSN_GRP_CD = "HLD_RLZ_RSN";
    private static final String ETC_RSN_CD = "ETC";

    private final InvRepository invRepository;
    private final InvHldRepository invHldRepository;
    private final InvHldAcrstRepository invHldAcrstRepository;
    private final InvHldRlzAcrstRepository invHldRlzAcrstRepository;
    private final CodeDetailRepository codeDetailRepository;
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
     * @return 발급된 보류 번호 목록
     */
    @Transactional
    public List<String> register(InvHldRegisterRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("보류 대상이 없습니다.");
        }
        List<String> hldNos = new ArrayList<>();
        for (InvHldRegisterRequest.Item item : request.getItems()) {
            hldNos.add(registerOne(item));
        }
        return hldNos;
    }

    private String registerOne(InvHldRegisterRequest.Item item) {
        if (item.getQty() == null || item.getQty() < 1) {
            throw new IllegalArgumentException("보류수량은 1 이상이어야 합니다.");
        }
        String rsnDscr = validateRsn(HLD_RSN_GRP_CD, "보류사유", item.getRsnCd(), item.getRsnDscr());

        // 재고 행 락 — 보류(hld) 증감의 직렬화 지점 (예약이 같은 행을 잡는 지점과 동일)
        Inv inv = invRepository.findByIdForUpdate(item.getInvId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 재고입니다: " + item.getInvId()));
        Prod prodEntity = inv.getProd();
        Lot lotEntity = inv.getLot();
        Loc locEntity = inv.getLoc();

        // v1 보류 대상은 보관 재고만 — 스테이징까지 허용하면 적치·출고확정의 수량 체크에도 파급이 생긴다
        if (locEntity.getLocTyp() != LocTyp.STORAGE) {
            throw new IllegalArgumentException("보관 로케이션의 재고만 보류할 수 있습니다: " + locEntity.getLocCd());
        }
        // 동일 사유 미해제 중복 차단 — 사유가 다를 때만 같은 재고 행에 병존한다 (uq_inv_hld_open_rsn이 최후 방어)
        if (invHldRepository.existsByProdIdAndLocIdAndLotIdAndRsnCdAndStatus(
                prodEntity.getId(), locEntity.getId(), lotEntity.getId(), item.getRsnCd(), InvHldStatus.HELD)) {
            throw new IllegalArgumentException("같은 사유의 미해제 보류가 이미 있습니다 (사유 " + item.getRsnCd() + "): "
                    + prodEntity.getProdCd() + " @ " + locEntity.getLocCd());
        }
        // 예약과 보류는 배타 — 보류는 가용재고에서만 잡는다 (예약 잔량이 있어도 남은 가용분은 보류 가능)
        if (item.getQty() > inv.avalQty()) {
            throw new IllegalArgumentException("보류수량이 가용재고를 초과했습니다 (가용 " + inv.avalQty() + "): "
                    + prodEntity.getProdCd() + " @ " + locEntity.getLocCd());
        }

        inv.hold(item.getQty());
        InvHld hld = InvHld.builder()
                .hldNo(nbrService.issue(HLD_NO_RULE_CD, LocalDate.now()))
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(item.getQty())
                .rsnCd(item.getRsnCd()).rsnDscr(rsnDscr)
                .build();
        invHldRepository.save(hld);
        // 실적은 자기완결 로그 — 건이 갱신돼도 등록 시점 기록이 보존된다
        invHldAcrstRepository.save(InvHldAcrst.builder()
                .hldNo(hld.getHldNo())
                .prod(prodEntity).loc(locEntity).lot(lotEntity)
                .hldQty(item.getQty())
                .rsnCd(item.getRsnCd()).rsnDscr(rsnDscr)
                .build());
        return hld.getHldNo();
    }

    /**
     * 보류 해제 (특정 보류 건 지목, 부분 해제 허용). 오등록 취소도 이 경로다 —
     * 별도 취소 상태 없이 해제(사유: 오등록)로 흡수한다.
     */
    @Transactional
    public void release(Long hldId, InvHldReleaseRequest request) {
        if (request.getQty() == null || request.getQty() < 1) {
            throw new IllegalArgumentException("해제수량은 1 이상이어야 합니다.");
        }
        String rsnDscr = validateRsn(HLD_RLZ_RSN_GRP_CD, "해제사유", request.getRsnCd(), request.getRsnDscr());

        // 보류 건 락 → inv 행 락 순서 (등록은 inv 행만 잡으므로 순서 역전이 없다)
        InvHld hld = invHldRepository.findByIdForUpdate(hldId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보류 건입니다: " + hldId));
        if (hld.getStatus() != InvHldStatus.HELD) {
            throw new IllegalArgumentException("보류중 상태의 건만 해제할 수 있습니다 (현재 " + hld.getStatus().getLabel() + "): " + hld.getHldNo());
        }
        if (request.getQty() > hld.remainingQty()) {
            throw new IllegalArgumentException("해제수량이 미해제 잔량을 초과했습니다 (잔량 " + hld.remainingQty() + "): " + hld.getHldNo());
        }

        // 보류 잔량이 있는 한 inv 행은 삭제되지 않으므로(ck_inv_qty: hld <= onHand → onHand > 0) 없으면 정합성 오류다
        Inv inv = invRepository.findByKeyForUpdate(hld.getProd().getId(), hld.getLoc().getId(), hld.getLot().getId())
                .orElseThrow(() -> new IllegalStateException("보류 건이 잡아둔 재고가 없습니다 (정합성 오류): " + hld.getHldNo()));
        if (inv.getHldQty() < request.getQty()) {
            throw new IllegalStateException("보류 잔량보다 재고의 보류 수량이 적습니다 (정합성 오류 — 보류 " + inv.getHldQty()
                    + " / 해제 " + request.getQty() + "): " + hld.getHldNo());
        }

        hld.release(request.getQty());
        inv.releaseHold(request.getQty());
        invHldRlzAcrstRepository.save(InvHldRlzAcrst.builder()
                .hldNo(hld.getHldNo())
                .prod(hld.getProd()).loc(hld.getLoc()).lot(hld.getLot())
                .rlzQty(request.getQty())
                .rsnCd(request.getRsnCd()).rsnDscr(rsnDscr)
                .build());
    }

    /**
     * 사유코드 검증 — 그룹에 존재해야 하고, ETC(기타)일 때만 텍스트 필수·그 외에는 무시(null 저장).
     * @return 저장할 사유 텍스트
     */
    private String validateRsn(String grpCd, String label, String rsnCd, String rsnDscr) {
        if (!StringUtils.hasText(rsnCd)) {
            throw new IllegalArgumentException(label + "를 선택해야 합니다.");
        }
        if (!codeDetailRepository.existsById(new CodeDetailId(grpCd, rsnCd))) {
            throw new IllegalArgumentException("존재하지 않는 " + label + " 코드입니다: " + rsnCd);
        }
        if (ETC_RSN_CD.equals(rsnCd)) {
            if (!StringUtils.hasText(rsnDscr)) {
                throw new IllegalArgumentException(label + "가 기타일 때는 사유 내용을 입력해야 합니다.");
            }
            return rsnDscr.trim();
        }
        return null;
    }
}
