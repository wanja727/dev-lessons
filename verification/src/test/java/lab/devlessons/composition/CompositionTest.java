package lab.devlessons.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "상속보다 조합"(composition-over-inheritance)의 주장을 확인한다.
 * 부모 Cart는 두 판이다. v1은 addAll이 안에서 add를 부르고(코드 cart), v2는 items.addAll로 한 번에 넣는다(코드 cartFast).
 */
class CompositionTest {

    private static final String LESSON = "composition-over-inheritance";
    private static final List<String> THREE = List.of("A", "B", "C");

    interface Cart {
        void add(String item);

        void addAll(List<String> list);
    }

    // ── 코드 cart (v1) ──
    static class CartV1 implements Cart {
        private final List<String> items = new ArrayList<>();

        public void add(String item) {
            items.add(item);
        }

        public void addAll(List<String> list) {
            for (String i : list) {
                add(i);
            }
        }
    }

    // ── 코드 cartFast (v2) ──
    static class CartV2 implements Cart {
        private final List<String> items = new ArrayList<>();

        public void add(String item) {
            items.add(item);
        }

        public void addAll(List<String> list) {
            items.addAll(list);
        }
    }

    // ── 코드 pointCart: v1을 상속하고 add와 addAll에서 센다 ──
    static class PointCartV1 extends CartV1 {
        int count = 0;

        @Override
        public void add(String item) {
            count++;
            super.add(item);
        }

        @Override
        public void addAll(List<String> list) {
            count += list.size();
            super.addAll(list);
        }
    }

    // 6이 나와서 addAll에서 세는 줄을 지운 자식. v1에서는 3이 맞게 나온다
    static class PointCartAddOnlyOnV1 extends CartV1 {
        int count = 0;

        @Override
        public void add(String item) {
            count++;
            super.add(item);
        }
    }

    // 같은 자식을 v2 위에 두면(부모가 바뀐 뒤)
    static class PointCartAddOnlyOnV2 extends CartV2 {
        int count = 0;

        @Override
        public void add(String item) {
            count++;
            super.add(item);
        }
    }

    // ── 코드 composed ──
    static class ComposedPointCart {
        private final Cart cart;
        int count = 0;

        ComposedPointCart(Cart cart) {
            this.cart = cart;
        }

        void add(String item) {
            count++;
            cart.add(item);
        }

        void addAll(List<String> list) {
            count += list.size();
            cart.addAll(list);
        }
    }

    @Test
    @DisplayName("상속판: v1에 세 개를 addAll하면 6, 세는 줄을 지우면 3, 부모가 v2로 바뀌면 0")
    void inheritanceDependsOnParentInternals() {
        PointCartV1 both = new PointCartV1();
        both.addAll(THREE);
        PointCartAddOnlyOnV1 addOnly = new PointCartAddOnlyOnV1();
        addOnly.addAll(THREE);
        PointCartAddOnlyOnV2 afterChange = new PointCartAddOnlyOnV2();
        afterChange.addAll(THREE);

        assertThat(both.count).isEqualTo(6);
        assertThat(addOnly.count).isEqualTo(3);
        assertThat(afterChange.count).isZero();

        CiOutput.record(LESSON, "inherit", "pointCart.addAll([A, B, C])\n\n"
                + "add와 addAll에서 모두 셀 때           → count " + both.count + "\n"
                + "addAll에서 세는 줄을 지웠을 때        → count " + addOnly.count + "\n"
                + "그 뒤 부모 addAll이 items.addAll로 바뀌면 → count " + afterChange.count);
    }

    @Test
    @DisplayName("조합판: Cart가 v1이든 v2든 addAll 세 개는 3")
    void compositionIgnoresInternals() {
        ComposedPointCart onV1 = new ComposedPointCart(new CartV1());
        onV1.addAll(THREE);
        ComposedPointCart onV2 = new ComposedPointCart(new CartV2());
        onV2.addAll(THREE);

        assertThat(onV1.count).isEqualTo(3);
        assertThat(onV2.count).isEqualTo(3);

        CiOutput.record(LESSON, "composed", "조합한 PointCart의 addAll([A, B, C])\n\n"
                + "Cart의 addAll이 add를 부를 때  → count " + onV1.count + "\n"
                + "Cart의 addAll이 items.addAll일 때 → count " + onV2.count);
    }

    @Test
    @DisplayName("Stack에 A, B를 넣고 add(0, X) 뒤 pop 셋은 B, A, X")
    void stackInheritsVector() {
        Stack<String> stack = new Stack<>();
        stack.push("A");
        stack.push("B");
        stack.add(0, "X");
        List<String> popped = List.of(stack.pop(), stack.pop(), stack.pop());

        assertThat(popped).containsExactly("B", "A", "X");
        CiOutput.record(LESSON, "stack", "pop() 세 번 → " + String.join(", ", popped));
    }

    @Test
    @DisplayName("ArrayDeque는 B, A 순서로 나오고 Deque에는 add(0, x)가 없다")
    void dequeHasNoInsertAt() {
        Deque<String> deque = new ArrayDeque<>();
        deque.push("A");
        deque.push("B");
        List<String> popped = List.of(deque.pop(), deque.pop());

        BrokenFiles r = BrokenFiles.compile(Map.of("UseDeque", """
                import java.util.*;
                class UseDeque {
                  void run() {
                    Deque<String> deque = new ArrayDeque<>();
                    deque.add(0, "X");
                  }
                }
                """));

        assertThat(popped).containsExactly("B", "A");
        assertThat(r.files()).containsExactly("UseDeque");
        CiOutput.record(LESSON, "deque", "deque.push(\"A\"); deque.push(\"B\");\npop() 두 번 → " + String.join(", ", popped)
                + "\n\ndeque.add(0, \"X\");\n→ 컴파일 오류\nerror: " + r.messages().get(0).substring("UseDeque: ".length()));
    }

    @Test
    @DisplayName("ArrayList를 필드로 가진 Members에서 set을 부르면 컴파일 오류")
    void composedMembersHidesSet() {
        BrokenFiles r = BrokenFiles.compile(Map.of(
                "Members", """
                        import java.util.*;
                        class Members {
                          private final List<String> list = new ArrayList<>();
                          void add(String email) {
                            if (!list.contains(email)) list.add(email);
                          }
                          int size() { return list.size(); }
                        }
                        """,
                "Use", """
                        class Use {
                          void run(Members m) { m.set(0, "a@b.c"); }
                        }
                        """));

        assertThat(r.files()).containsExactly("Use");
        assertThat(r.messages().get(0)).contains("cannot find symbol");
    }
}
