package com.project.wmsback.inventory.service;

import com.project.mdm.prod.entity.Prod;
import com.project.wmsback.inventory.dto.InvMovConfirmRequest;
import com.project.wmsback.inventory.entity.Inv;
import com.project.wmsback.inventory.entity.InvMovDvsn;
import com.project.wmsback.inventory.entity.InvMovTask;
import com.project.wmsback.inventory.repository.InvHistRepository;
import com.project.wmsback.inventory.repository.InvMovTaskRepository;
import com.project.wmsback.inventory.repository.InvRepository;
import com.project.wmsback.warehouse.entity.Loc;
import com.project.wmsback.warehouse.entity.LocTyp;
import com.project.wmsback.warehouse.entity.Lot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 락 순서 규칙의 동시성 시험 — 「같은 두 재고 행을 반대 순서로 요청해도 교착이 없다」를 확인한다.
 * docs/design.md 「락 순서」의 근거가 코드로만 남아 있어, 정렬을 지우면 깨지는 자리를 시험으로 세운다.
 *
 * <p><b>대조군을 함께 둔다.</b> 지금 코드는 모든 경로가 {@link InvStore}를 지나 같은 순서로 잠그므로
 * 정상 경로로는 교착을 만들 수 없다. 그래서 창구를 우회해 반대 순서로 직접 잠그는 시험을 하나 두어
 * 「이 시험 장치가 실제로 교착을 잡아낸다」를 먼저 보이고, 나머지 둘이 「정상 경로는 교착이 없다」를 본다.
 *
 * <p><b>대조군만 결정적이다.</b> 정렬된 경로는 두 스레드가 같은 행을 먼저 잡아 순서대로 밀리므로,
 * 교차가 일어나는 시점을 밖에서 만들 수 없다. 그래서 보장 시험은 경합을 여러 번 반복해 확인한다.
 *
 * <p>트랜잭션이 둘 이상 동시에 떠 있어야 해 목으로는 덮이지 않는다 — {@code DB_URL}이 있는 환경에서만
 * 돌고({@code WmsBackApplicationTests}와 같은 조건), 만든 데이터는 {@link #tearDown}이 지운다.
 * 클래스에 {@code @Transactional}을 붙이면 안 된다 — 한 트랜잭션 안에서는 교착 자체가 성립하지 않는다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class LockOrderConcurrencyTest {

    /** 실전 경로 시험의 반복 횟수 — 스레드 둘이 이 횟수만큼 서로 반대 순서로 확정한다 */
    private static final int ROUNDS = 10;
    /** 교착이면 1초(deadlock_timeout) 안에 DB가 끊는다. 그보다 오래 걸리면 시험이 매달린 것이라 실패로 본다 */
    private static final int WAIT_SECONDS = 30;

    @Autowired InvStore invStore;
    @Autowired InvRepository invRepository;
    @Autowired InvHistRepository invHistRepository;
    @Autowired InvMovTaskRepository invMovTaskRepository;
    @Autowired InvMovService invMovService;
    @Autowired PlatformTransactionManager txManager;

    @PersistenceContext EntityManager em;

    private TransactionTemplate tx;
    private ExecutorService pool;

    private Prod prod;
    private Lot lot;
    private Loc fromA;
    private Loc fromB;
    private Loc to;
    private InvKey keyA;
    private InvKey keyB;
    private String tag;
    private final List<Long> createdInvIds = new ArrayList<>();
    private final List<String> createdMovNos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        pool = Executors.newFixedThreadPool(2);
        tag = Long.toString(System.nanoTime(), 36);

        lot = firstRow("select l from Lot l order by l.id", Lot.class);
        Assumptions.assumeTrue(lot != null, "시험할 Lot이 없다");
        prod = lot.getProd();

        // 이 (상품, Lot)이 아직 쓰지 않은 보관 로케이션 셋을 빌린다 — 기존 재고 행을 건드리지 않으려는 것
        List<Loc> free = em.createQuery("""
                        select c from Loc c
                         where c.locTyp = :typ
                           and c.id not in (select i.loc.id from Inv i where i.prod = :prod and i.lot = :lot)
                         order by c.id
                        """, Loc.class)
                .setParameter("typ", LocTyp.STORAGE)
                .setParameter("prod", prod)
                .setParameter("lot", lot)
                .setMaxResults(3)
                .getResultList();
        Assumptions.assumeTrue(free.size() == 3, "빈 보관 로케이션이 3개 필요하다");
        fromA = free.get(0);
        fromB = free.get(1);
        to = free.get(2);

        keyA = new InvKey(prod.getId(), fromA.getId(), lot.getId());
        keyB = new InvKey(prod.getId(), fromB.getId(), lot.getId());
    }

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
        tx.executeWithoutResult(status -> {
            if (!createdMovNos.isEmpty()) {
                em.createQuery("delete from InvHist h where h.rfnDocNo in :nos")
                        .setParameter("nos", createdMovNos).executeUpdate();
                em.createQuery("delete from InvMovTask t where t.invMovNo in :nos")
                        .setParameter("nos", createdMovNos).executeUpdate();
            }
            // 이동확정이 만든 도착지 행까지 지운다 — 시험이 빌려 쓴 세 로케이션의 (상품, Lot) 행 전부
            em.createQuery("delete from Inv i where i.prod = :prod and i.lot = :lot and i.loc.id in :locs")
                    .setParameter("prod", prod).setParameter("lot", lot)
                    .setParameter("locs", List.of(fromA.getId(), fromB.getId(), to.getId()))
                    .executeUpdate();
        });
        createdInvIds.clear();
        createdMovNos.clear();
    }

    @Test
    @DisplayName("대조군 — 창구를 우회해 반대 순서로 잠그면 실제로 교착이 난다 (시험 장치 검증)")
    void oppositeOrderWithoutInvStore_deadlocks() throws Exception {
        createInv(fromA, 10L, 0L);
        createInv(fromB, 10L, 0L);

        CyclicBarrier bothHoldOne = new CyclicBarrier(2);
        // 각자 한 행씩 잡고 마주 본 뒤 상대의 행을 요청한다 — 순환이라 DB가 한쪽을 끊는다
        Future<Void> first = pool.submit(lockBoth(keyA, keyB, bothHoldOne));
        Future<Void> second = pool.submit(lockBoth(keyB, keyA, bothHoldOne));

        List<Throwable> failures = collectFailures(first, second);
        assertEquals(1, failures.size(), "교착은 한쪽만 끊는다 (남은 쪽은 그대로 진행한다)");
        assertTrue(isLockFailure(failures.get(0)),
                "교착은 락 획득 실패로 올라와야 한다: " + failures.get(0));
    }

    @Test
    @DisplayName("InvStore.lockAll은 요청 순서와 무관하게 재고 키 오름차순으로 잠근다")
    void lockAllSortsKeysRegardlessOfRequestOrder() {
        createInv(fromA, 10L, 0L);
        createInv(fromB, 10L, 0L);

        List<InvKey> asked = List.of(keyB, keyA);
        List<InvKey> locked = tx.execute(status -> new ArrayList<>(invStore.lockAll(asked).keySet()));

        assertEquals(List.of(keyA, keyB), locked,
                "요청은 B → A였지만 락은 키 오름차순(A → B)이어야 한다 — 이 정렬이 교착을 막는 장치다");
    }

    @Test
    @DisplayName("같은 두 재고 행을 반대 순서로 동시에 잠가도 InvStore를 지나면 교착이 없다")
    void oppositeOrderThroughInvStore_doesNotDeadlock() throws Exception {
        createInv(fromA, 10L, 0L);
        createInv(fromB, 10L, 0L);

        CyclicBarrier start = new CyclicBarrier(2);
        Future<Void> first = pool.submit(lockAllRepeatedly(List.of(keyA, keyB), start));
        Future<Void> second = pool.submit(lockAllRepeatedly(List.of(keyB, keyA), start));

        assertEquals(List.of(), collectFailures(first, second),
                "정렬된 순서로만 잡으므로 서로 기다리다 끊기는 일이 없어야 한다");
    }

    @Test
    @DisplayName("이동확정 다건 — 두 지시를 반대 순서로 실은 요청이 겹쳐도 교착 없이 누계가 맞는다")
    void invMovConfirm_oppositeItemOrder_doesNotDeadlock() throws Exception {
        long total = ROUNDS * 2L;                 // 지시 하나가 스레드 둘에게서 라운드마다 1씩 확정된다
        createInv(fromA, total, total);           // 예약을 든 지시가 소진할 몫
        createInv(fromB, total, total);
        createInv(to, 0L, 0L);                    // 도착 행을 미리 만들어 둔다 (동시 생성은 uq_inv가 막는 자리라 시험 대상이 아니다)
        InvMovTask taskA = createTask(fromA, total);
        InvMovTask taskB = createTask(fromB, total);

        CyclicBarrier start = new CyclicBarrier(2);
        Future<Void> first = pool.submit(confirmRepeatedly(taskA.getId(), taskB.getId(), start));
        Future<Void> second = pool.submit(confirmRepeatedly(taskB.getId(), taskA.getId(), start));

        assertEquals(List.of(), collectFailures(first, second),
                "확정은 재고 행을 키 오름차순으로 먼저 잠그므로 요청의 지시 순서는 락 순서를 바꾸지 못한다");

        assertEquals(total, reload(taskA).getCmplQty(), "지시 A의 완료수량이 실제 확정분과 같아야 한다");
        assertEquals(total, reload(taskB).getCmplQty(), "지시 B의 완료수량이 실제 확정분과 같아야 한다");
        assertEquals(total * 2, onHandOf(to), "도착지 실재고는 두 지시의 확정 합이어야 한다");
    }

    // ── 작업 ────────────────────────────────────────────────────────────────

    /** 창구를 우회한 직접 락 두 건 — 대조군 전용이다. 서비스 코드가 이렇게 잠그면 안 된다 */
    private Callable<Void> lockBoth(InvKey mine, InvKey theirs, CyclicBarrier bothHoldOne) {
        return () -> tx.execute(status -> {
            invRepository.findByKeyForUpdate(mine.prodId(), mine.locId(), mine.lotId());
            await(bothHoldOne);
            invRepository.findByKeyForUpdate(theirs.prodId(), theirs.locId(), theirs.lotId());
            return null;
        });
    }

    private Callable<Void> lockAllRepeatedly(List<InvKey> keys, CyclicBarrier start) {
        return () -> {
            await(start);
            for (int i = 0; i < ROUNDS; i++) {
                tx.executeWithoutResult(status -> invStore.lockAll(keys));
            }
            return null;
        };
    }

    private Callable<Void> confirmRepeatedly(Long firstTaskId, Long secondTaskId, CyclicBarrier start) {
        return () -> {
            await(start);
            for (int i = 0; i < ROUNDS; i++) {
                invMovService.confirm(confirmRequest(firstTaskId, secondTaskId));
            }
            return null;
        };
    }

    private static InvMovConfirmRequest confirmRequest(Long... taskIds) {
        List<InvMovConfirmRequest.Item> items = new ArrayList<>();
        for (Long taskId : taskIds) {
            InvMovConfirmRequest.Item item = new InvMovConfirmRequest.Item();
            item.setTaskId(taskId);
            item.setQty(1L);
            items.add(item);
        }
        InvMovConfirmRequest request = new InvMovConfirmRequest();
        request.setItems(items);
        return request;
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    /** 시험용 재고 행. 이력을 남기지 않는다 — 이 시험이 보는 것은 락 순서뿐이고, tearDown이 행째 지운다 */
    private void createInv(Loc loc, long onHand, long aloc) {
        Long id = tx.execute(status -> {
            Inv inv = Inv.builder().prod(prod).loc(loc).lot(lot).build();
            if (onHand > 0) {
                inv.increaseOnHand(onHand);
            }
            if (aloc > 0) {
                inv.reserve(aloc);
            }
            return invRepository.save(inv).getId();
        });
        createdInvIds.add(id);
    }

    private InvMovTask createTask(Loc from, long qty) {
        String movNo = "MV-TEST-" + tag + "-" + from.getId();
        createdMovNos.add(movNo);
        return tx.execute(status -> invMovTaskRepository.save(InvMovTask.builder()
                .invMovNo(movNo)
                .movDvsn(InvMovDvsn.INV_MOV)
                .prod(prod).lot(lot)
                .fromLoc(from).toLoc(to)
                .drctQty(qty)
                .build()));
    }

    private InvMovTask reload(InvMovTask task) {
        return tx.execute(status -> invMovTaskRepository.findById(task.getId()).orElseThrow());
    }

    private long onHandOf(Loc loc) {
        Inv inv = tx.execute(status -> invRepository
                .findByKeyForUpdate(prod.getId(), loc.getId(), lot.getId()).orElse(null));
        return inv != null ? inv.getOnHandQty() : 0L;
    }

    private <T> T firstRow(String jpql, Class<T> type) {
        return em.createQuery(jpql, type).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 두 스레드를 같은 지점에 세운다. 여기서 매달리면 시험 자체가 잘못된 것이라 예외로 올린다 */
    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("동시 실행 지점에서 상대를 기다리지 못했다", e);
        }
    }

    /** 두 작업을 끝까지 기다리고 실패만 모은다 — 한쪽이 끊겨도 다른 쪽 결과를 봐야 한다 */
    private static List<Throwable> collectFailures(Future<?>... futures) throws Exception {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        return failures;
    }

    /**
     * 락 획득 실패인가 — 교착(40P01)은 드라이버·번역기를 거치며 예외 타입이 갈리므로 사유 문구도 함께 본다
     * (락 대기 상한에 걸린 경우도 같은 계열이라 여기 걸린다).
     */
    private static boolean isLockFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof PessimisticLockingFailureException) {
                return true;
            }
            String message = t.getMessage();
            if (message != null && message.toLowerCase().contains("deadlock")) {
                return true;
            }
        }
        return false;
    }
}
