package com.project.wmsback.outbound.entity;

import com.project.wmsback.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 출고 웨이브. 재고 할당의 단위 — 릴리즈 시 소속 주문 전체를 한 번에 FEFO 할당한다.
 * 릴리즈 이후 진행(피킹/확정)은 주문 단위라 웨이브는 여기서 역할이 끝난다.
 *
 * 편성은 주문 쪽에서 관리한다(OutbOrder.assignWave). orders 컬렉션은 편성 현황
 * 집계(주문 수)용 읽기 전용 매핑으로, cascade/orphanRemoval을 두지 않는다 —
 * 주문은 웨이브보다 오래 살고 재편성될 수 있는 독립 애그리거트다.
 */
@Entity
@Table(name = "outb_wave")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutbWave extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outb_wave_id")
    private Long id;

    /** 웨이브 번호 (업무 식별자, 예: WV-20260718-001) */
    @Column(name = "wave_no", nullable = false, length = 30, unique = true)
    private String waveNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private WaveStatus status;

    /** 릴리즈(할당 실행) 시각 */
    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @OneToMany(mappedBy = "wave")
    private List<OutbOrder> orders = new ArrayList<>();

    @Builder
    private OutbWave(String waveNo) {
        this.waveNo = waveNo;
        this.status = WaveStatus.PLANNED;
    }

    /** 편성 변경(주문 담기/빼기/해체)은 릴리즈 전(PLANNED)에만 허용 */
    public void assertPlanned() {
        if (status != WaveStatus.PLANNED) {
            throw new IllegalStateException("이미 릴리즈된 웨이브는 편성을 변경할 수 없습니다: " + waveNo);
        }
    }
}
