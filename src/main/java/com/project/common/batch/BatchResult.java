package com.project.common.batch;

import java.util.List;

/**
 * 일괄 처리 결과. 성공한 id와 실패한 id·사유를 나눠 돌려준다 —
 * 한 요청으로 여러 건을 처리하되 건별 성공/실패는 그대로 알려주기 위한 응답 형태다.
 * 화면은 id로 자기 행(주문번호 등)을 찾아 요약한다.
 */
public record BatchResult(List<Long> succeeded, List<Failure> failed) {

    public record Failure(Long id, String reason) {
    }
}
