package com.project.wmsback.strategy.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.wmsback.strategy.core.dto.RvsnResponse;
import com.project.wmsback.strategy.core.entity.StgyRvsn;
import com.project.wmsback.strategy.core.entity.StgyTyp;
import com.project.wmsback.strategy.core.repository.StgyRvsnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리비전 스냅샷 기록·조회. 스냅샷 쓰기는 전략 저장과 같은 트랜잭션이다 —
 * 저장은 됐는데 리비전이 없는 상태를 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StgyRvsnService {

    private final StgyRvsnRepository stgyRvsnRepository;
    private final ObjectMapper objectMapper;

    /** 전략 저장 트랜잭션 안에서 호출된다 (별도 트랜잭션이 아님 — 원자성 유지) */
    @Transactional
    public void snapshot(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, Object definition) {
        stgyRvsnRepository.save(StgyRvsn.builder()
                .stgyTyp(stgyTyp)
                .stgyId(stgyId)
                .rvsnNo(rvsnNo)
                .snpsht(toJson(definition))
                .build());
    }

    public List<RvsnResponse> list(StgyTyp stgyTyp, Long stgyId) {
        return stgyRvsnRepository.findAllByStgyTypAndStgyIdOrderByRvsnNoDesc(stgyTyp, stgyId)
                .stream().map(RvsnResponse::from).toList();
    }

    /** 스냅샷을 JSON 트리로 반환 — 응답에 문자열이 아닌 객체로 심긴다 */
    public JsonNode snapshotTree(StgyTyp stgyTyp, Long stgyId, Long rvsnNo) {
        StgyRvsn rvsn = stgyRvsnRepository.findByStgyTypAndStgyIdAndRvsnNo(stgyTyp, stgyId, rvsnNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리비전입니다: " + rvsnNo));
        return readTree(rvsn.getSnpsht());
    }

    /** 유형별 전략마다 최신 리비전 1행 — 삭제된 전략의 이력/복원 진입점 (D4) */
    public List<StgyRvsn> latestPerStrategy(StgyTyp stgyTyp) {
        return stgyRvsnRepository.findLatestPerStrategy(stgyTyp);
    }

    /** 스냅샷에서 전략명만 추출 (삭제된 전략 목록 표시용) */
    public String snapshotName(StgyRvsn rvsn) {
        return readTree(rvsn.getSnpsht()).path("stgyNm").asText("(이름 없음)");
    }

    /** 복원용 — 스냅샷을 정의 DTO로 역직렬화 */
    public <T> T snapshotAs(StgyTyp stgyTyp, Long stgyId, Long rvsnNo, Class<T> type) {
        StgyRvsn rvsn = stgyRvsnRepository.findByStgyTypAndStgyIdAndRvsnNo(stgyTyp, stgyId, rvsnNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리비전입니다: " + rvsnNo));
        try {
            return objectMapper.readValue(rvsn.getSnpsht(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리비전 스냅샷을 읽을 수 없습니다: " + rvsnNo, e);
        }
    }

    private String toJson(Object definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("전략 정의 직렬화에 실패했습니다.", e);
        }
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리비전 스냅샷을 읽을 수 없습니다.", e);
        }
    }
}
