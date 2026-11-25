package lab.devlessons.valuereference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * 레슨 "값과 참조"(J2)의 주장을 검증한다. 순수 JUnit 테스트와 스프링 컨텍스트 하나.
 */
class ValueReferenceTest {

    private static final String LESSON = "value-and-reference";

    static class Order {
        int price;

        Order(int price) {
            this.price = price;
        }
    }

    static class Member {
        String name;
        int age;
        boolean active;
    }

    // 코드 predict
    static void discount(Order o, int rate) {
        o.price = o.price * (100 - rate) / 100;
        rate = 0;
    }

    // 코드 reassign
    static void replace(Order o) {
        o = new Order(1);
    }

    @Test
    @DisplayName("코드 predict: 필드를 바꾼 것은 보이고, 기본형 매개변수를 바꾼 것은 보이지 않는다 (9000, 10)")
    void predict() {
        Order order = new Order(10000);
        int rate = 10;
        discount(order, rate);

        assertThat(order.price).isEqualTo(9000);
        assertThat(rate).isEqualTo(10);
        CiOutput.record(LESSON, "predict", order.price + "\n" + rate);
    }

    @Test
    @DisplayName("코드 reassign: 매개변수에 새 객체를 대입해도 호출한 쪽은 그대로다 (10000)")
    void reassign() {
        Order order = new Order(10000);
        replace(order);

        assertThat(order.price).isEqualTo(10000);
        CiOutput.record(LESSON, "reassign", String.valueOf(order.price));
    }

    @Test
    @DisplayName("참조형을 대입하면 같은 객체를 가리킨다. 배열도 같다")
    void assignmentCopiesReference() {
        int a = 10;
        int b = a;
        b = 20;

        Order x = new Order(10000);
        Order y = x;
        y.price = 5000;

        int[] arr = {1, 2};
        int[] alias = arr;
        alias[0] = 9;

        int[] keep = {1, 2};
        int[] other = keep;
        other = new int[] {7, 8};
        other[0] = 9;

        assertThat(a).isEqualTo(10);
        assertThat(x.price).isEqualTo(5000);
        assertThat(arr[0]).isEqualTo(9);
        assertThat(keep[0]).isEqualTo(1);
        CiOutput.record(LESSON, "assign", """
                a = %d
                x.price = %d
                arr[0] = %d""".formatted(a, x.price, arr[0]));
    }

    static void add(List<String> names) {
        names.add("lee");
        names = new ArrayList<>();
        names.add("park");
    }

    static void clear(List<String> items) {
        items = new ArrayList<>();
    }

    @Test
    @DisplayName("리스트를 넘기면 add는 보이고, 매개변수에 새 리스트를 대입한 뒤의 일은 보이지 않는다")
    void listParameter() {
        List<String> names = new ArrayList<>(List.of("kim"));
        add(names);
        List<String> items = new ArrayList<>(List.of("a", "b", "c"));
        clear(items);

        assertThat(names).containsExactly("kim", "lee");
        assertThat(items).hasSize(3);
        CiOutput.record(LESSON, "list-param", names + "\n" + items.size());
    }

    @Test
    @DisplayName("==는 변수에 든 것을 비교한다. 재정의하지 않은 equals는 ==와 같고, String의 equals는 글자를 비교한다")
    void equalityOperator() {
        int i = 10000;
        int j = 10000;
        Order a = new Order(10000);
        Order b = new Order(10000);
        Order c = a;
        String s = new String("PAID");
        String t = new String("PAID");

        assertThat(i == j).isTrue();
        assertThat(a == b).isFalse();
        assertThat(a == c).isTrue();
        assertThat(a.equals(b)).isFalse();
        assertThat(s == t).isFalse();
        assertThat(s.equals(t)).isTrue();
        CiOutput.record(LESSON, "equality", """
                i == j        → %s
                a == b        → %s
                a == c        → %s
                a.equals(b)   → %s
                s == t        → %s
                s.equals(t)   → %s""".formatted(i == j, a == b, a == c, a.equals(b), s == t, s.equals(t)));
    }

    @Test
    @DisplayName("값을 주지 않은 필드는 0, false, null이고, null에 점을 찍으면 무엇이 null인지 알려 주는 NPE가 난다")
    void nullAndDefaults() {
        Member m = new Member();
        Order[] orders = new Order[3];

        assertThat(m.age).isZero();
        assertThat(m.active).isFalse();
        assertThat(m.name).isNull();
        assertThat(orders[0]).isNull();
        assertThatThrownBy(() -> m.name.length())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("String.length()")
                .hasMessageContaining("name");

        String message = null;
        try {
            m.name.length();
        } catch (NullPointerException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "null", """
                m.age    → %d
                m.active → %s
                m.name   → %s
                m.name.length()
                → NullPointerException: %s""".formatted(m.age, m.active, m.name, message));
    }

    @Test
    @DisplayName("null과의 비교: ==는 안전하고, 리터럴 쪽 equals와 Objects.equals도 안전하다")
    void nullSafeComparison() {
        String status = null;

        assertThat(status == null).isTrue();
        assertThat("PAID".equals(status)).isFalse();
        assertThat(Objects.equals(null, null)).isTrue();
        assertThatThrownBy(() -> status.equals("PAID")).isInstanceOf(NullPointerException.class);
        CiOutput.record(LESSON, "null-compare", """
                status == null          → true
                "PAID".equals(status)   → false
                status.equals("PAID")   → NullPointerException""");
    }

    // 실무 구역: 싱글톤 빈이 필드의 리스트를 그대로 돌려준다
    static class CategoryService {
        private final List<String> categories = new ArrayList<>(List.of("도서", "음반"));

        List<String> categories() {
            return categories;
        }
    }

    static class SafeCategoryService {
        private final List<String> categories = new ArrayList<>(List.of("도서", "음반"));

        List<String> categories() {
            return List.copyOf(categories);
        }
    }

    @Test
    @DisplayName("싱글톤 빈이 필드의 리스트를 그대로 돌려주면 한 호출이 더한 원소가 다음 호출에서 보인다. List.copyOf면 보이지 않는다")
    void singletonBeanSharesList() {
        try (var ctx = new AnnotationConfigApplicationContext(CategoryService.class, SafeCategoryService.class)) {
            // 요청 1: 받은 리스트를 화면용으로 고친다
            List<String> first = ctx.getBean(CategoryService.class).categories();
            first.add("전체");
            // 요청 2: 같은 빈에 다시 묻는다
            List<String> second = ctx.getBean(CategoryService.class).categories();

            List<String> safeFirst = ctx.getBean(SafeCategoryService.class).categories();
            Throwable error = null;
            try {
                safeFirst.add("전체");
            } catch (UnsupportedOperationException e) {
                error = e;
            }
            List<String> safeSecond = ctx.getBean(SafeCategoryService.class).categories();

            assertThat(ctx.getBean(CategoryService.class)).isSameAs(ctx.getBean(CategoryService.class));
            assertThat(second).containsExactly("도서", "음반", "전체");
            assertThat(error).isInstanceOf(UnsupportedOperationException.class);
            assertThat(safeSecond).containsExactly("도서", "음반");
            CiOutput.record(LESSON, "singleton-list", """
                    그대로 돌려줄 때
                    요청 1이 받은 리스트에 "전체"를 더함
                    요청 2가 받은 리스트 → %s

                    List.copyOf로 돌려줄 때
                    요청 1의 add → %s
                    요청 2가 받은 리스트 → %s""".formatted(second, error.getClass().getSimpleName(), safeSecond));
        }
    }
}
