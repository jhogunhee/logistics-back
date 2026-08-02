package com.project.wmsback.strategy.core.dto;

/** 동적 선택지 1건 (GET /strategy/meta/options/{source}) */
public record OptionResponse(String value, String label) {
}
