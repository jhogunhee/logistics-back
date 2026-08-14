package com.project.wmsback.strategy.inspection.exception;

import com.project.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 검수 위반이 전용 핸들러(400 + violations)로 내려가는지.
 * <p>
 * advice가 둘이면 예외 매칭은 advice 사이에서는 타입 구체성이 아니라 <b>등록 순서</b>가 먼저다 —
 * 컴포넌트 스캔이 common(Global)을 wmsback(Inspection)보다 먼저 올리므로, 순서를 못박지 않으면
 * Global의 Exception 핸들러가 위반 예외를 삼켜 500 「서버 오류」가 된다. 2026-08-14 검수 화면이
 * 위반 배너 대신 서버 오류 토스트를 띄운 실제 결함이라 운영과 같은 등록 순서로 고정해 검증한다.
 */
class InspectionExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @PostMapping("/test/receive")
        void receive() {
            throw new InspectionViolationException(List.of(
                    new InspectionViolationException.LineViolation(
                            1L, "PROD-0001", "SHELF_LIFE_PCT", "유통기한 잔여비율",
                            "잔여 10.0% < 기준 30%", "10.0%", "30% 이상")));
        }
    }

    @Test
    @DisplayName("검수 위반은 공통 Exception 핸들러(500)가 아니라 전용 핸들러의 400 + violations로 내려간다")
    void violationGoesToInspectionHandlerNotGlobal() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler(), new InspectionExceptionHandler())
                .build();

        mvc.perform(post("/test/receive"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].ruleCd").value("SHELF_LIFE_PCT"))
                .andExpect(jsonPath("$.message").value("검수 제약 위반 1건 — 검수 저장이 거부되었습니다."));
    }
}
