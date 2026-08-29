package com.project.wmsback.worker.service;

import com.project.common.dto.PageCond;
import com.project.common.dto.PageResponse;
import com.project.wmsback.worker.dto.WrkrAcrstCnt;
import com.project.wmsback.worker.dto.WrkrAcrstDailyResponse;
import com.project.wmsback.worker.dto.WrkrAcrstDetailResponse;
import com.project.wmsback.worker.dto.WrkrAcrstGroup;
import com.project.wmsback.worker.dto.WrkrAcrstSearchCond;
import com.project.wmsback.worker.dto.WrkrAcrstSummaryResponse;
import com.project.wmsback.worker.dto.WrkrOptionResponse;
import com.project.wmsback.worker.entity.WrkrWorkTyp;
import com.project.wmsback.worker.repository.WrkrAcrstQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 작업자 실적 집계. 조회가 꺼내 온 {@code (tx_typ, rfn_doc_typ)} 묶음을 작업 종류로 접어
 * 「작업자 한 명 = 한 행」·「하루 = 한 점」으로 만든다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WrkrAcrstService {

    private final WrkrAcrstQueryRepository wrkrAcrstQueryRepository;

    /** 작업자별 요약. 많이 한 사람이 위로 온다 */
    public List<WrkrAcrstSummaryResponse> summary(WrkrAcrstSearchCond cond) {
        Map<String, Fold> byWorker = new LinkedHashMap<>();
        for (WrkrAcrstGroup group : wrkrAcrstQueryRepository.summary(cond)) {
            byWorker.computeIfAbsent(group.loginId(), key -> new Fold(group.usrNm())).add(group);
        }

        return byWorker.entrySet().stream()
                .map(e -> e.getValue().toSummary(e.getKey()))
                .sorted(Comparator.comparingLong(WrkrAcrstSummaryResponse::totCnt).reversed()
                        .thenComparing(WrkrAcrstSummaryResponse::loginId))
                .toList();
    }

    /** 일자별 추이. 실적이 없는 날은 행이 없다 (화면이 기간을 알고 있어 빈 날을 채운다) */
    public List<WrkrAcrstDailyResponse> daily(WrkrAcrstSearchCond cond) {
        Map<LocalDate, Fold> byDate = new LinkedHashMap<>();
        for (WrkrAcrstGroup group : wrkrAcrstQueryRepository.daily(cond)) {
            byDate.computeIfAbsent(group.workDt(), key -> new Fold(null)).add(group);
        }

        List<WrkrAcrstDailyResponse> rows = new ArrayList<>();
        byDate.forEach((workDt, fold) -> rows.add(fold.toDaily(workDt)));
        return rows;
    }

    public List<WrkrOptionResponse> workers(WrkrAcrstSearchCond cond) {
        return wrkrAcrstQueryRepository.workers(cond);
    }

    public PageResponse<WrkrAcrstDetailResponse> detail(WrkrAcrstSearchCond cond, PageCond pageCond) {
        return wrkrAcrstQueryRepository.detail(cond, pageCond);
    }

    /**
     * 한 축(작업자 또는 하루)에 모이는 묶음들의 누적. 같은 작업 종류로 접히는 조합이 둘 이상일 수
     * 있어 더하면서 담는다.
     */
    private static final class Fold {

        private final String usrNm;
        private final Map<WrkrWorkTyp, WrkrAcrstCnt> byWorkTyp = new EnumMap<>(WrkrWorkTyp.class);
        private long totCnt;
        private long totQty;

        private Fold(String usrNm) {
            this.usrNm = usrNm;
        }

        private void add(WrkrAcrstGroup group) {
            WrkrWorkTyp workTyp = WrkrWorkTyp.of(group.txTyp(), group.rfnDocTyp());
            if (workTyp == null) {
                return;
            }
            byWorkTyp.merge(workTyp, new WrkrAcrstCnt(group.cnt(), group.qty()),
                    (a, b) -> new WrkrAcrstCnt(a.cnt() + b.cnt(), a.qty() + b.qty()));
            totCnt += group.cnt();
            totQty += group.qty();
        }

        private WrkrAcrstSummaryResponse toSummary(String loginId) {
            return new WrkrAcrstSummaryResponse(loginId, usrNm, totCnt, totQty, byWorkTyp);
        }

        private WrkrAcrstDailyResponse toDaily(LocalDate workDt) {
            return new WrkrAcrstDailyResponse(workDt, totCnt, totQty, byWorkTyp);
        }
    }
}
