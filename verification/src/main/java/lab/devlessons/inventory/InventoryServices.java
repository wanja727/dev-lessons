package lab.devlessons.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 레슨 "재고 차감 동시성": DB로 재고를 줄이는 네 가지 방법. 재고가 없으면 SoldOutException */
public final class InventoryServices {

    private InventoryServices() {
    }

    public static class SoldOutException extends RuntimeException {
        public SoldOutException() {
            super("품절");
        }
    }

    /** 1. 읽고-계산하고-쓰기: 조회한 값에서 1을 빼서 저장한다 (변경 감지) */
    @Service
    public static class ReadModifyWriteInventoryService {
        private final InventoryItemRepository items;
        /** 테스트에서 "읽은 뒤, 쓰기 전" 틈을 넓히려고 끼워 넣는 지점 */
        private volatile Runnable afterRead = () -> { };

        public ReadModifyWriteInventoryService(InventoryItemRepository items) {
            this.items = items;
        }

        public void setAfterRead(Runnable afterRead) {
            this.afterRead = afterRead;
        }

        @Transactional
        public void order(Long itemId) {
            InventoryItem item = items.findById(itemId).orElseThrow();
            if (item.getQuantity() < 1) {
                throw new SoldOutException();
            }
            afterRead.run();
            item.decrease(); // 커밋 때 UPDATE inventory_item SET quantity = (읽은 값 - 1)
        }
    }

    /** 2. 조건부 UPDATE: 확인과 차감을 SQL 한 문장으로 */
    @Service
    public static class ConditionalUpdateInventoryService {
        private final InventoryItemRepository items;

        public ConditionalUpdateInventoryService(InventoryItemRepository items) {
            this.items = items;
        }

        @Transactional
        public void order(Long itemId) {
            if (items.decreaseIfAvailable(itemId) == 0) {
                throw new SoldOutException();
            }
        }
    }

    /** 3. 비관적 락: 읽을 때 행을 잠가 다른 트랜잭션을 기다리게 한다 */
    @Service
    public static class PessimisticInventoryService {
        private final InventoryItemRepository items;

        public PessimisticInventoryService(InventoryItemRepository items) {
            this.items = items;
        }

        @Transactional
        public void order(Long itemId) {
            InventoryItem item = items.findForUpdate(itemId).orElseThrow();
            if (item.getQuantity() < 1) {
                throw new SoldOutException();
            }
            item.decrease();
        }
    }

    /** 4. 낙관적 락: 버전이 바뀌었으면 커밋 때 실패한다 (재시도 없음) */
    @Service
    public static class OptimisticInventoryService {
        private final VersionedInventoryItemRepository items;

        public OptimisticInventoryService(VersionedInventoryItemRepository items) {
            this.items = items;
        }

        @Transactional
        public void order(Long itemId) {
            VersionedInventoryItem item = items.findById(itemId).orElseThrow();
            if (item.getQuantity() < 1) {
                throw new SoldOutException();
            }
            item.decrease();
        }
    }
}
