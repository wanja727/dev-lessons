package lab.devlessons.lock;

import java.util.concurrent.atomic.AtomicInteger;


import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 재고 차감의 세 가지 방식. afterRead는 테스트에서 "읽은 직후"를 붙잡아 두는 지점이다. */
public final class StockServices {

    private StockServices() {
    }

    @Service
    public static class StockService {
        private final PlainStockRepository plain;
        private final VersionedStockRepository versioned;

        public StockService(PlainStockRepository plain, VersionedStockRepository versioned) {
            this.plain = plain;
            this.versioned = versioned;
        }

        /** 락 없음: 읽고, 줄이고, 변경 감지로 UPDATE */
        @Transactional
        public void decreaseNoLock(Long id, int n, Runnable afterRead) {
            var stock = plain.findById(id).orElseThrow();
            afterRead.run();
            stock.decrease(n);
        }

        /** 낙관적 락: 커밋 때 버전이 바뀌었으면 실패 */
        @Transactional
        public void decreaseOptimistic(Long id, int n, Runnable afterRead) {
            var stock = versioned.findById(id).orElseThrow();
            afterRead.run();
            stock.decrease(n);
        }

        /** 비관적 락: 읽을 때부터 행을 잠근다 */
        @Transactional
        public void decreasePessimistic(Long id, int n, Runnable afterRead) {
            var stock = plain.findForUpdate(id).orElseThrow();
            afterRead.run();
            stock.decrease(n);
        }

        @Transactional
        public void decreasePessimisticNoWait(Long id, int n) {
            plain.findForUpdateNoWait(id).orElseThrow().decrease(n);
        }
    }

    /** 낙관적 락 충돌 시 새 트랜잭션으로 다시 시도한다. 재시도는 반드시 트랜잭션 바깥(다른 빈)에서 */
    @Service
    public static class OptimisticRetryService {
        private final StockService stockService;
        private final AtomicInteger retries = new AtomicInteger();

        public OptimisticRetryService(StockService stockService) {
            this.stockService = stockService;
        }

        public void decrease(Long id, int n) {
            for (int attempt = 1; ; attempt++) {
                try {
                    stockService.decreaseOptimistic(id, n, () -> { });
                    return;
                } catch (ObjectOptimisticLockingFailureException conflict) {
                    retries.incrementAndGet();
                    if (attempt >= 50) {
                        throw conflict;
                    }
                }
            }
        }

        public int retries() {
            return retries.get();
        }

        public void resetRetries() {
            retries.set(0);
        }
    }
}
