package lab.devlessons.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 레슨 "동시 요청에서 재고 지키기"의 실무 구역을 검증한다. 테스트에는 트랜잭션이 없다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class InventoryPracticeTest {

    static final String LESSON = "stock-concurrency";

    @Autowired InventoryItemRepository items;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        items.deleteAll();
        items.save(new InventoryItem(1L, 50));
    }

    @Test
    @DisplayName("@Modifying 쿼리로 줄인 뒤, 같은 트랜잭션에서 앞서 읽은 엔티티는 옛 수량이다")
    void modifyingQueryLeavesStaleEntity() {
        List<String> log = new ArrayList<>();
        tx.executeWithoutResult(s -> {
            InventoryItem item = items.findById(1L).orElseThrow();
            log.add("findById → 수량 " + item.getQuantity());
            int changed = items.decreaseIfAvailable(1L);
            log.add("decreaseIfAvailable → 바뀐 행 " + changed);
            log.add("앞서 읽은 item.getQuantity() → " + item.getQuantity());
            log.add("다시 findById → " + items.findById(1L).orElseThrow().getQuantity() + " (1차 캐시)");
            Integer db = jdbc.queryForObject("select quantity from inventory_item where id = 1", Integer.class);
            log.add("DB의 수량 → " + db);
            assertThat(item.getQuantity()).isEqualTo(50);
            assertThat(db).isEqualTo(49);
        });
        CiOutput.record(LESSON, "modifying", String.join("\n", log));
    }

    @Test
    @DisplayName("트랜잭션 없이 비관적 락 쿼리를 부르면 오류가 난다")
    void lockQueryWithoutTransaction() {
        String outcome;
        try {
            items.findForUpdate(1L);
            outcome = "정상으로 끝남";
        } catch (RuntimeException e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            outcome = e.getClass().getSimpleName() + "\n  원인: " + root.getClass().getSimpleName() + ": " + root.getMessage();
        }

        assertThat(outcome).doesNotStartWith("정상");
        CiOutput.record(LESSON, "lock-no-tx", "[@Transactional 없이 findForUpdate(1)]\n" + outcome);
    }
}
