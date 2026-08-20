package com.project.wmsback.inventory.repository;

import com.querydsl.core.types.EntityPath;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import static com.project.wmsback.inbound.entity.QPutawayTask.putawayTask;
import static com.project.wmsback.inventory.entity.QInv.inv;
import static com.project.wmsback.inventory.entity.QInvHist.invHist;
import static com.project.wmsback.inventory.entity.QInvMovTask.invMovTask;
import static com.project.wmsback.inventory.entity.QInvStktkLn.invStktkLn;
import static com.project.wmsback.warehouse.entity.QFxngLoc.fxngLoc;

/**
 * 로케이션을 참조 중인 테이블이 있는지 확인하는 조회 — 로케이션 삭제·변경 가드가 쓴다.
 * <p>
 * FK가 0건이라 DB가 막아주지 않으므로 "누가 이 로케이션을 쓰는가"를 애플리케이션이 세야 하는데,
 * 그 목록(재고 · 이력 · 이동지시 · 적치지시 · 실사 · 고정 로케이션)을 <b>여기서만</b> 안다 — 서비스에 흩으면
 * 참조 테이블이 늘 때 확인을 빠뜨리기 쉽다. 참조 대상 엔티티들이 사는 곳이라 이 패키지에 둔다
 * ({@link LocCapacityQueryRepository}와 같은 배치).
 * <p>
 * Spring Data 인터페이스 없이 {@code JPAQueryFactory}만 드는 읽기 전용 조회 포트다 —
 * 저장이 없고 동적 조건도 없어 3파일 삼각형을 만들 이유가 없다.
 */
@Repository
@RequiredArgsConstructor
public class LocRefQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 이 로케이션에 재고가 놓여 있는가 — 온도대·유형 변경 가드가 쓴다 */
    public boolean existsInv(Long locId) {
        return exists(inv.loc.id.eq(locId), inv);
    }

    /**
     * 이 로케이션을 참조 중이면 사용자에게 보일 이름("재고" · "이동지시" 등), 아니면 null.
     * 첫 참조에서 멈춘다 — 몇 건인지는 필요 없고, 삭제를 막을 이유 하나면 충분하다.
     * 순서는 사용자가 납득하기 쉬운 쪽부터다 (재고 → 이력 → 지시 → 실사).
     */
    public String findAnyReference(Long locId) {
        if (existsInv(locId)) return "재고";
        if (exists(invHist.loc.id.eq(locId), invHist)) return "재고 이력";
        if (exists(invMovTask.fromLoc.id.eq(locId).or(invMovTask.toLoc.id.eq(locId)), invMovTask)) return "이동지시";
        if (exists(putawayTask.toLoc.id.eq(locId), putawayTask)) return "적치지시";
        if (exists(invStktkLn.loc.id.eq(locId), invStktkLn)) return "재고실사";
        if (exists(fxngLoc.loc.id.eq(locId), fxngLoc)) return "고정 로케이션 마스터";
        return null;
    }

    private boolean exists(BooleanExpression where, EntityPath<?> from) {
        return queryFactory.selectOne().from(from).where(where).fetchFirst() != null;
    }
}
