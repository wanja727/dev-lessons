package lab.devlessons.jvmmemory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "JVM 메모리 구조"(J14)의 주장을 검증한다. 힙 크기에 따라 달라지는 것은 -Xmx를 준 자식 JVM에서 본다.
 */
class JvmMemoryTest {

    private static final String LESSON = "jvm-memory";

    static int depth;

    static void dive() {
        depth++;
        dive();
    }

    @Test
    @DisplayName("끝나는 조건이 없는 재귀는 StackOverflowError를 던진다")
    void stackOverflow() {
        depth = 0;
        Throwable error = null;
        try {
            dive();
        } catch (StackOverflowError e) {
            error = e;
        }
        assertThat(error).isInstanceOf(StackOverflowError.class);
        assertThat(depth).isGreaterThan(1000);
        StackTraceElement[] trace = error.getStackTrace();
        StringBuilder text = new StringBuilder("java.lang.StackOverflowError\n");
        for (int i = 0; i < 3; i++) {
            text.append("\tat JvmMemoryTest.dive(JvmMemoryTest.java:").append(trace[i].getLineNumber()).append(")\n");
        }
        text.append("\t... (같은 줄이 계속)\n\n에러가 났을 때 depth → ").append(String.format("%,d", depth));
        CiOutput.record(LESSON, "predict", text.toString());
    }

    @Test
    @DisplayName("한 스레드에서 StackOverflowError가 난 뒤에도 다른 스레드는 정상으로 메서드를 부른다")
    void stackIsPerThread() throws Exception {
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();
        Thread a = new Thread(() -> {
            try {
                dive();
            } catch (StackOverflowError e) {
                first.set("StackOverflowError");
            }
        });
        a.start();
        a.join();
        Thread b = new Thread(() -> second.set("주문 " + createOrder(10000).price));
        b.start();
        b.join();

        assertThat(first.get()).isEqualTo("StackOverflowError");
        assertThat(second.get()).isEqualTo("주문 11000");
        CiOutput.record(LESSON, "per-thread", "스레드 A: dive() → " + first.get() + "\n스레드 B: createOrder(10000) → "
                + second.get());
    }

    static int framesAtBottom;

    static int sum(int n) {
        if (n == 0) {
            framesAtBottom = (int) java.util.Arrays.stream(Thread.currentThread().getStackTrace())
                    .filter(f -> f.getMethodName().equals("sum")).count();
            return 0;
        }
        return n + sum(n - 1);
    }

    @Test
    @DisplayName("sum(3)에서 가장 깊을 때 sum의 프레임은 4개다")
    void framesPerCall() {
        assertThat(sum(3)).isEqualTo(6);
        assertThat(framesAtBottom).isEqualTo(4);
        CiOutput.record(LESSON, "frames", "sum(3)에서 n이 0일 때 스택에 쌓인 sum 프레임 → " + framesAtBottom);
    }

    static class Order {
        int price;

        Order(int price) {
            this.price = price;
        }
    }

    static Order createOrder(int price) {
        int tax = price / 10;
        Order order = new Order(price + tax);
        return order;
    }

    @Test
    @DisplayName("메서드가 돌려준 객체는 메서드가 끝난 뒤에도 남아 있다")
    void objectOutlivesFrame() {
        Order o = createOrder(10000);
        assertThat(o.price).isEqualTo(11000);
        CiOutput.record(LESSON, "create", String.valueOf(o.price));
    }

    /** 자식 JVM: 1MB 배열을 계속 만들어 리스트에 담는다 */
    public static class HeapFill {
        public static void main(String[] args) {
            List<byte[]> list = new ArrayList<>();
            System.out.println("최대 힙 " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
            while (true) {
                list.add(new byte[1024 * 1024]);
                if (list.size() % 8 == 0) {
                    System.out.println("담은 배열 " + list.size() + "MB");
                }
            }
        }
    }

    /** 자식 JVM: 같은 양을 만들지만 담지 않는다 */
    public static class HeapChurn {
        public static void main(String[] args) {
            long made = 0;
            for (int i = 0; i < 1024; i++) {
                byte[] b = new byte[1024 * 1024];
                made += b.length;
            }
            System.out.println("만든 배열 " + made / 1024 / 1024 + "MB, 끝까지 돌았음");
        }
    }

    @Test
    @DisplayName("가리키고 있는 객체로 힙이 차면 OutOfMemoryError: Java heap space, 가리키지 않으면 같은 양에도 나지 않는다")
    void heapFill() {
        ChildJvm.Run fill = ChildJvm.run(List.of("-Xmx64m"), HeapFill.class);
        ChildJvm.Run churn = ChildJvm.run(List.of("-Xmx64m"), HeapChurn.class);

        assertThat(fill.exitCode()).isNotZero();
        assertThat(fill.stderr()).contains("java.lang.OutOfMemoryError: Java heap space");
        assertThat(churn.exitCode()).isZero();
        assertThat(churn.stdout()).contains("1024MB");

        String fillOut = fill.stdout().strip().lines().reduce((a, b) -> b).orElse("");
        String errLine = fill.stderr().lines().filter(l -> l.contains("OutOfMemoryError")).findFirst().orElse("");
        CiOutput.record(LESSON, "heap-fill", """
                java -Xmx64m HeapFill (리스트에 담음)
                %s
                %s
                %s

                java -Xmx64m HeapChurn (담지 않음, 1MB 배열 1024개)
                %s""".formatted(fill.stdout().lines().findFirst().orElse(""), fillOut, errLine,
                churn.stdout().strip()));
    }

    /** 자식 JVM: 최대 힙 크기를 출력한다 */
    public static class MaxMemory {
        public static void main(String[] args) {
            System.out.println(Runtime.getRuntime().maxMemory() / 1024 / 1024);
        }
    }

    @Test
    @DisplayName("Runtime.maxMemory()는 -Xmx로 준 값에 가깝다")
    void maxMemory() {
        ChildJvm.Run r = ChildJvm.run(List.of("-Xmx256m"), MaxMemory.class);
        long mb = Long.parseLong(r.stdout().strip());
        assertThat(mb).isBetween(200L, 256L);
        CiOutput.record(LESSON, "max-memory", "java -Xmx256m → Runtime.maxMemory() = " + mb + "MB");
    }

    // 실무 구역: 서로를 출력하는 toString
    static class Member {
        List<Ord> orders = new ArrayList<>();

        @Override
        public String toString() {
            return "Member(orders=" + orders + ")";
        }
    }

    static class Ord {
        Member member;

        @Override
        public String toString() {
            return "Order(member=" + member + ")";
        }
    }

    static class SafeOrd {
        long id = 1;
        Member member;

        @Override
        public String toString() {
            return "Order(id=" + id + ")";
        }
    }

    @Test
    @DisplayName("서로를 출력하는 두 toString은 StackOverflowError를 던지고, 한쪽에서 연관을 빼면 문자열을 돌려준다")
    void bidirectionalToString() {
        Member m = new Member();
        Ord o = new Ord();
        o.member = m;
        m.orders.add(o);

        Throwable error = null;
        try {
            m.toString();
        } catch (StackOverflowError e) {
            error = e;
        }
        Member m2 = new Member();
        SafeOrd s = new SafeOrd();
        s.member = m2;

        assertThat(error).isInstanceOf(StackOverflowError.class);
        assertThat(s.toString()).isEqualTo("Order(id=1)");
        StackTraceElement[] trace = error.getStackTrace();
        StringBuilder text = new StringBuilder("member.toString()\n→ java.lang.StackOverflowError\n");
        for (int i = 0; i < 6; i++) {
            String cls = trace[i].getClassName().replace("lab.devlessons.jvmmemory.JvmMemoryTest$", "");
            text.append("\tat ").append(cls).append('.').append(trace[i].getMethodName()).append('\n');
        }
        text.append("\t...\n\n고친 뒤 order.toString()\n→ ").append(s);
        CiOutput.record(LESSON, "to-string", text.toString());
    }
}
