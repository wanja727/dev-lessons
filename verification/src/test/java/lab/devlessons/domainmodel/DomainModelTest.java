package lab.devlessons.domainmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import lab.devlessons.support.SqlCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** 레슨 "도메인 모델과 트랜잭션 스크립트"의 주장을 실제 PostgreSQL에서 검증한다. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerConfig.class)
class DomainModelTest {

    static final String LESSON = "domain-model";

    @Autowired TestEntityManager em;

    // ── 트랜잭션 스크립트: 규칙이 서비스 메서드에 있다 ──

    void scriptCancel(Long orderId) {
        ScriptOrder o = em.find(ScriptOrder.class, orderId);
        if (o.getStatus() == OrderStatus.SHIPPED) {
            throw new CannotCancel(o.getStatus());
        }
        o.setStatus(OrderStatus.CANCELED);
        A2Product p = o.getProduct();
        p.setStock(p.getStock() + o.getQuantity());
    }

    /** 다른 사람이 새로 만든 일괄 취소. 확인을 빠뜨렸다 */
    void scriptBulkCancel(List<Long> ids) {
        for (Long id : ids) {
            ScriptOrder o = em.find(ScriptOrder.class, id);
            o.setStatus(OrderStatus.CANCELED);
        }
    }

    // ── 도메인 모델: 서비스는 꺼내서 메서드를 부른다 ──

    void modelCancel(Long orderId) {
        em.find(ModelOrder.class, orderId).cancel();
    }

    @Test
    @DisplayName("트랜잭션 스크립트와 도메인 모델로 짠 취소가 같은 상태와 재고를 만든다")
    void sameResult() {
        A2Product p1 = em.persist(new A2Product(10));
        ScriptOrder script = em.persist(new ScriptOrder(OrderStatus.PAID, p1, 2));
        A2Product p2 = em.persist(new A2Product(10));
        ModelOrder model = new ModelOrder(1L);
        model.addLine(p2, 2, 5_000);
        em.persist(model);
        em.flush();
        em.clear();

        scriptCancel(script.getId());
        modelCancel(model.getId());
        em.flush();
        em.clear();

        ScriptOrder s = em.find(ScriptOrder.class, script.getId());
        ModelOrder m = em.find(ModelOrder.class, model.getId());
        assertThat(s.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(m.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(em.find(A2Product.class, p1.getId()).getStock()).isEqualTo(12);
        assertThat(em.find(A2Product.class, p2.getId()).getStock()).isEqualTo(12);
        CiOutput.record(LESSON, "same", "[재고 10, 2개 주문을 취소]\n"
                + "트랜잭션 스크립트: 상태 " + s.getStatus() + ", 재고 " + em.find(A2Product.class, p1.getId()).getStock() + "\n"
                + "도메인 모델: 상태 " + m.getStatus() + ", 재고 " + em.find(A2Product.class, p2.getId()).getStock());
    }

    @Test
    @DisplayName("세터가 열린 주문으로 짠 일괄 취소는 배송된 주문도 취소한다")
    void bulkCancelSkipsRule() {
        A2Product p = em.persist(new A2Product(10));
        ScriptOrder paid = em.persist(new ScriptOrder(OrderStatus.PAID, p, 1));
        ScriptOrder shipped = em.persist(new ScriptOrder(OrderStatus.SHIPPED, p, 1));
        em.flush();
        em.clear();

        scriptBulkCancel(List.of(paid.getId(), shipped.getId()));
        em.flush();
        em.clear();

        OrderStatus after = em.find(ScriptOrder.class, shipped.getId()).getStatus();
        assertThat(after).isEqualTo(OrderStatus.CANCELED);
        CiOutput.record(LESSON, "bulk", "[일괄 취소: PAID 주문 1건, SHIPPED 주문 1건]\n"
                + "PAID 주문 → " + em.find(ScriptOrder.class, paid.getId()).getStatus() + "\n"
                + "SHIPPED 주문 → " + after + "  (배송된 주문이 취소됨)");
    }

    @Test
    @DisplayName("도메인 모델의 cancel은 배송된 주문에서 CannotCancel을 던진다. DB 없이 확인한다")
    void modelRejectsShipped() {
        long t0 = System.nanoTime();
        ModelOrder order = new ModelOrder(1L);
        order.addLine(new A2Product(10), 1, 5_000);
        order.ship();

        assertThatThrownBy(order::cancel).isInstanceOf(CannotCancel.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        long micros = (System.nanoTime() - t0) / 1_000;
        String message;
        try {
            order.cancel();
            message = "취소됨";
        } catch (CannotCancel e) {
            message = "CannotCancel: " + e.getMessage();
        }
        CiOutput.record(LESSON, "reject", "[배송된 주문에 cancel()]\n" + message + "\n"
                + "상태: " + order.getStatus() + "\n"
                + "(new ModelOrder로 만들어 확인. DB와 스프링 없이 " + Math.max(1, micros / 1000) + "ms 안쪽)");
    }

    @Test
    @DisplayName("엔티티 메서드가 필드만 바꿔도 flush할 때 UPDATE가 나간다")
    void dirtyCheckingSendsUpdate() {
        A2Product p = em.persist(new A2Product(10));
        ModelOrder order = new ModelOrder(1L);
        order.addLine(p, 2, 5_000);
        em.persist(order);
        em.flush();
        em.clear();

        ModelOrder found = em.find(ModelOrder.class, order.getId());
        found.getLines().size(); // 주문 상품을 불러 둔다
        em.find(A2Product.class, p.getId());
        SqlCapture.clear();
        found.cancel(); // save를 부르지 않는다
        em.flush();

        List<String> updates = SqlCapture.sqls().stream().filter(s -> s.startsWith("update")).toList();
        assertThat(updates).hasSize(2);
        CiOutput.record(LESSON, "update", "[order.cancel() 뒤 flush, save는 부르지 않음]\n"
                + String.join("\n", updates));
    }

    @Test
    @DisplayName("주문 상품은 order.addLine으로만 생기고 금액이 함께 바뀐다. 주문 상품에는 바깥에서 바꾸는 공개 메서드가 없다")
    void aggregateRoot() {
        ModelOrder order = new ModelOrder(1L);
        order.addLine(new A2Product(10), 2, 5_000);
        order.addLine(new A2Product(10), 1, 3_000);

        assertThat(order.getTotalPrice()).isEqualTo(13_000);
        List<String> mutators = Arrays.stream(ModelOrderLine.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .filter(n -> !n.startsWith("get"))
                .toList();
        assertThat(mutators).isEmpty();
        assertThatThrownBy(() -> order.getLines().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
