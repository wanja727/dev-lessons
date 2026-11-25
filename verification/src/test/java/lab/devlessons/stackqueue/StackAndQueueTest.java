package lab.devlessons.stackqueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.Vector;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "스택과 큐"(K3)의 주장을 검증한다.
 * ArrayDeque의 안쪽 배열(elements)과 head, tail은 java.base 모듈 안의 private 필드라서,
 * --add-opens를 준 자식 JVM에서 리플렉션으로 읽는다.
 */
class StackAndQueueTest {

    private static final String LESSON = "stack-and-queue";

    @Test
    @DisplayName("1, 2, 3을 넣으면 스택은 3을, 큐는 1을 먼저 꺼낸다")
    void lifoAndFifo() {
        Deque<Integer> stack = new ArrayDeque<>();
        Deque<Integer> queue = new ArrayDeque<>();
        for (int no = 1; no <= 3; no++) {
            stack.push(no);
            queue.offer(no);
        }
        int s = stack.pop();
        int q = queue.poll();
        assertThat(s).isEqualTo(3);
        assertThat(q).isEqualTo(1);
        List<Integer> restStack = new ArrayList<>();
        List<Integer> restQueue = new ArrayList<>();
        while (!stack.isEmpty()) {
            restStack.add(stack.pop());
        }
        while (!queue.isEmpty()) {
            restQueue.add(queue.poll());
        }
        assertThat(restStack).containsExactly(2, 1);
        assertThat(restQueue).containsExactly(2, 3);
        CiOutput.record(LESSON, "predict", s + "\n" + q + "\n\n남은 것을 모두 꺼내면\nstack: " + restStack + "\nqueue: " + restQueue);
    }

    static boolean balanced(String s) {
        Deque<Character> st = new ArrayDeque<>();
        for (char c : s.toCharArray()) {
            if (c == '(') {
                st.push(c);
            } else if (c == ')') {
                if (st.isEmpty()) {
                    return false;
                }
                st.pop();
            }
        }
        return st.isEmpty();
    }

    @Test
    @DisplayName("괄호 짝 맞추기: (())는 true, (()와 ())는 false")
    void brackets() {
        assertThat(balanced("(())")).isTrue();
        assertThat(balanced("(()")).isFalse();
        assertThat(balanced("())")).isFalse();
        assertThat(balanced(")(")).isFalse();
        CiOutput.record(LESSON, "brackets", """
                balanced("(())") → %s
                balanced("(()")  → %s
                balanced("())")  → %s""".formatted(balanced("(())"), balanced("(()"), balanced("())")));
    }

    @Test
    @DisplayName("빈 ArrayDeque에서 poll과 peek은 null, pop과 remove와 element는 NoSuchElementException이다. null은 넣을 수 없다")
    void emptyAndNull() {
        Deque<Integer> d = new ArrayDeque<>();
        assertThat(d.poll()).isNull();
        assertThat(d.peek()).isNull();
        assertThatThrownBy(d::pop).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(d::remove).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(d::element).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> d.offer(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> d.push(null)).isInstanceOf(NullPointerException.class);
        CiOutput.record(LESSON, "empty", """
                poll()  → null
                peek()  → null
                pop()   → NoSuchElementException
                remove() → NoSuchElementException
                offer(null) → NullPointerException""");
    }

    @Test
    @DisplayName("Stack은 Vector를 상속해 중간 원소를 건드리는 메서드가 열려 있고, 메서드가 동기화되어 있다")
    void legacyStack() throws Exception {
        Stack<Integer> st = new Stack<>();
        st.push(1);
        st.push(2);
        st.push(3);
        assertThat(st).isInstanceOf(Vector.class);
        st.add(0, 99); // 스택이 해서는 안 될 일
        assertThat(st.get(0)).isEqualTo(99);
        assertThat(java.lang.reflect.Modifier.isSynchronized(
                Vector.class.getMethod("add", Object.class).getModifiers())).isTrue();
        assertThat(java.lang.reflect.Modifier.isSynchronized(
                Stack.class.getMethod("pop").getModifiers())).isTrue();
    }

    /** 자식 JVM에서 돈다. 길이 8의 ArrayDeque와 ArrayList에 같은 일을 시키고 칸을 출력한다 */
    public static final class RingProbe {

        static String state(ArrayDeque<?> d) throws Exception {
            Field e = ArrayDeque.class.getDeclaredField("elements");
            Field h = ArrayDeque.class.getDeclaredField("head");
            Field t = ArrayDeque.class.getDeclaredField("tail");
            e.setAccessible(true);
            h.setAccessible(true);
            t.setAccessible(true);
            Object[] arr = (Object[]) e.get(d);
            StringBuilder cells = new StringBuilder();
            for (Object o : arr) {
                cells.append(o == null ? "_" : o.toString()).append(' ');
            }
            return "cells " + cells.toString().strip() + " head " + h.get(d) + " tail " + t.get(d);
        }

        public static void main(String[] args) throws Exception {
            ArrayDeque<Integer> d = new ArrayDeque<>(7);
            System.out.println("start " + state(d));
            for (int i = 1; i <= 5; i++) {
                d.offer(i);
            }
            System.out.println("offer1to5 " + state(d));
            Object[] before = arrayOf(d).clone();
            d.poll();
            Object[] afterOne = arrayOf(d);
            int moved = 0;
            for (int i = 0; i < before.length; i++) {
                if (before[i] != null && afterOne[i] != null && !before[i].equals(afterOne[i])) {
                    moved++;
                }
            }
            System.out.println("moved " + moved);
            d.poll();
            System.out.println("poll2 " + state(d));
            for (int i = 6; i <= 8; i++) {
                d.offer(i);
            }
            System.out.println("offer6to8 " + state(d));
            d.offer(9);
            System.out.println("offer9 " + state(d));
            System.out.println("order " + d);
        }

        static Object[] arrayOf(ArrayDeque<?> d) throws Exception {
            Field e = ArrayDeque.class.getDeclaredField("elements");
            e.setAccessible(true);
            return (Object[]) e.get(d);
        }
    }

    @Test
    @DisplayName("ArrayDeque는 poll해도 다른 원소의 칸이 바뀌지 않고 head만 옮긴다. 배열 끝에 닿으면 앞의 빈 칸으로 돌아가 이어 쓴다")
    void ringBuffer() {
        ChildJvm.Run r = ChildJvm.run(List.of("--add-opens", "java.base/java.util=ALL-UNNAMED"), RingProbe.class);
        assertThat(r.exitCode()).as(r.stderr()).isZero();
        List<String> lines = r.stdout().lines().toList();
        assertThat(lines).containsExactly(
                "start cells _ _ _ _ _ _ _ _ head 0 tail 0",
                "offer1to5 cells 1 2 3 4 5 _ _ _ head 0 tail 5",
                "moved 0",
                "poll2 cells _ _ 3 4 5 _ _ _ head 2 tail 5",
                "offer6to8 cells _ _ 3 4 5 6 7 8 head 2 tail 0",
                "offer9 cells 9 _ 3 4 5 6 7 8 head 2 tail 1",
                "order [3, 4, 5, 6, 7, 8, 9]");

        // 같은 일을 ArrayList로 하면 꺼낼 때마다 남은 원소를 당긴다
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        int shifted = 0;
        for (int i = 0; i < 2; i++) {
            shifted += list.size() - 1;
            list.remove(0);
        }
        assertThat(shifted).isEqualTo(7);
        assertThat(list).containsExactly(3, 4, 5);

        CiOutput.record(LESSON, "ring", lines.stream()
                .filter(l -> !l.startsWith("moved"))
                .map(l -> l.replace("cells ", "[").replace(" head", "]\n  head")
                        .replace("start ", "처음\n  ")
                        .replace("offer1to5 ", "offer 1~5\n  ")
                        .replace("poll2 ", "poll 두 번\n  ")
                        .replace("offer6to8 ", "offer 6~8\n  ")
                        .replace("offer9 ", "offer 9\n  ")
                        .replace("order ", "꺼내는 순서\n  "))
                .reduce((a, b) -> a + "\n" + b).orElseThrow()
                + "\npoll 한 번에 칸을 옮긴 원소: " + lines.get(2).split(" ")[1] + "개");
    }

    @Test
    @DisplayName("원소 10만 개를 모두 꺼내는 데 ArrayList.remove(0)이 ArrayDeque.poll보다 훨씬 오래 걸린다")
    void drainTiming() {
        for (int i = 0; i < 5; i++) {
            drainList(10_000);
            drainDeque(10_000);
        }
        long list = drainList(100_000);
        long deque = drainDeque(100_000);
        assertThat(deque).isLessThan(list);
        CiOutput.record(LESSON, "drain", """
                원소 100,000개를 맨 앞에서 모두 꺼내기
                ArrayList.remove(0): %,dms
                ArrayDeque.poll():   %,dms""".formatted(list, deque));
    }

    static long drainList(int n) {
        List<Integer> q = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            q.add(i);
        }
        long start = System.nanoTime();
        long sum = 0;
        while (!q.isEmpty()) {
            sum += q.remove(0);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(sum).isEqualTo((long) n * (n - 1) / 2);
        return ms;
    }

    static long drainDeque(int n) {
        Deque<Integer> q = new ArrayDeque<>(n);
        for (int i = 0; i < n; i++) {
            q.offer(i);
        }
        long start = System.nanoTime();
        long sum = 0;
        while (!q.isEmpty()) {
            sum += q.poll();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(sum).isEqualTo((long) n * (n - 1) / 2);
        return ms;
    }

    @Test
    @DisplayName("한 Deque에 offer와 pop을 섞으면 '홈'이 나오고, push로 넣으면 '상품 상세'가 나온다")
    void mixedMethods() {
        Deque<String> history = new ArrayDeque<>();
        history.offer("홈");
        history.offer("상품 목록");
        history.offer("상품 상세");
        String mixed = history.pop();

        Deque<String> fixed = new ArrayDeque<>();
        fixed.push("홈");
        fixed.push("상품 목록");
        fixed.push("상품 상세");
        String fixedOut = fixed.pop();

        assertThat(mixed).isEqualTo("홈");
        assertThat(fixedOut).isEqualTo("상품 상세");
        assertThat(Arrays.asList(fixed.toArray())).containsExactly("상품 목록", "홈");
        CiOutput.record(LESSON, "mixed", "offer로 넣고 pop: " + mixed + "\npush로 넣고 pop: " + fixedOut);
    }
}
