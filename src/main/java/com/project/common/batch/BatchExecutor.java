package com.project.common.batch;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 여러 건을 한 요청으로 받아 건별로 처리하고 성공/실패를 나눠 모은다.
 *
 * <p>트랜잭션은 요청 전체가 아니라 <b>건별</b>이다. 배치를 한 트랜잭션으로 묶으면 한 건의 거부가
 * 나머지 전부를 롤백시켜 "실패한 건만 알려주고 나머지는 처리한다"는 의도가 깨진다.
 * 그래서 항목마다 REQUIRES_NEW 트랜잭션을 열고, 예외는 그 경계 밖에서 잡아 사유로 남긴다.
 *
 * <p>애노테이션 대신 TransactionTemplate을 쓰는 이유는 StgyExecLogService와 같다 — 같은 빈 안에서
 * 자기 메서드를 부르면 프록시를 타지 않아 @Transactional이 걸리지 않고, 커밋 시점 예외도
 * 메서드 안의 catch로는 잡히지 않기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class BatchExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchExecutor.class);

    private final PlatformTransactionManager transactionManager;

    /**
     * id만 받는 일괄 처리 — 확정·확정취소·삭제처럼 대상 id 외에 넘길 것이 없을 때.
     *
     * @param ids    처리할 대상 id 목록. 받은 순서대로 처리한다 (채번 순서가 화면 순서와 일치)
     * @param action id 한 건을 처리하는 업무 — 이 안에서 던진 RuntimeException은 그 건만 롤백·실패 처리된다
     */
    public BatchResult run(List<Long> ids, Consumer<Long> action) {
        return run(ids, Function.identity(), action);
    }

    /**
     * 건마다 다른 파라미터를 받는 일괄 처리 — 항목이 {@code { id, qty, memo }} 같은 DTO여도 된다.
     * 실행기는 항목을 결과에서 무엇으로 식별할지({@code idOf})만 알면 되고, 항목의 형태는 상관하지 않는다.
     * 배치 전체에 공통인 파라미터는 이 오버로드 없이 클로저로 넘기면 된다: {@code run(ids, id -> cancel(id, reason))}.
     *
     * @param items  처리할 항목 목록. 받은 순서대로 처리한다
     * @param idOf   항목 → 결과(BatchResult)에 남길 id
     * @param action 항목 한 건을 처리하는 업무 — 이 안에서 던진 RuntimeException은 그 건만 롤백·실패 처리된다
     */
    public <T> BatchResult run(List<T> items, Function<T, Long> idOf, Consumer<T> action) {
        TransactionTemplate perItem = new TransactionTemplate(transactionManager);
        perItem.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Long> succeeded = new ArrayList<>();
        List<BatchResult.Failure> failed = new ArrayList<>();
        for (T item : items) {
            Long id = idOf.apply(item);
            try {
                perItem.executeWithoutResult(status -> action.accept(item));
                succeeded.add(id);
            } catch (IllegalArgumentException | IllegalStateException e) {
                // 업무 규칙 거부 — 메시지가 곧 사용자에게 보여줄 사유다 (GlobalExceptionHandler와 같은 취급)
                failed.add(new BatchResult.Failure(id, e.getMessage()));
            } catch (RuntimeException e) {
                log.error("일괄 처리 중 예외 (id={})", id, e);
                failed.add(new BatchResult.Failure(id, "처리 중 오류가 발생했습니다."));
            }
        }
        return new BatchResult(succeeded, failed);
    }
}
