package com.project.wmsback.warehouse.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

/**
 * 업무구분. 존이 담당하는 업무.
 * 값 목록은 공통코드 BIZ_DVSN과 같아야 한다 (화면 드롭다운이 공통코드를 읽는다).
 */
@Getter
@RequiredArgsConstructor
public enum BizDvsn {
    INB("입고작업"),
    OUTB("출고작업"),
    STRG("보관"),
    PIKNG("피킹"),
    RTNGS("반품"),
    WRK("작업");

    /**
     * 대기 구역 존 — 입고작업(입고대기)·출고작업(출고대기). 실물이 잠깐 머무는 자리라
     * 적치·출고확정이 수량을 소진 중이고, 그 사이의 장부 조작은 세는 시점이 불안정하다.
     * <p>
     * 로케이션 유형({@code LocTyp.STAGE})과 <b>따로 두는 이유</b>: 유형은 로케이션마다 자유롭게
     * 정해지므로 대기존에 {@code STORAGE} 로케이션을 하나 등록하면 유형 필터만으로는 통과한다.
     * 존 업무구분은 그 자리가 무엇을 하는 곳인지의 선언이라 두 겹이 서로를 대신하지 않는다.
     * <p>
     * 값 목록의 주인이 공통코드라 서비스가 특정 값에 결합하지 않는다는 규칙의 명시적 예외이고,
     * 그 결합을 여기 한 곳으로 모은다 ({@code RplnDestinationResolver.inPikngZon} ·
     * {@code RtngsLocResolver.inRtngsZon}과 같은 성격 — 그쪽은 판정이 한 곳뿐이라 자기 클래스에 있다).
     */
    public static final Set<BizDvsn> STAGING = EnumSet.of(INB, OUTB);

    private final String label;

    /** 이 존이 대기 구역인가 ({@link #STAGING}) */
    public boolean staging() {
        return STAGING.contains(this);
    }
}
