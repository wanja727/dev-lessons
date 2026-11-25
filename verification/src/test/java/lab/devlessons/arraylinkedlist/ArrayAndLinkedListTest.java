package lab.devlessons.arraylinkedlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "배열과 연결 리스트"(K2)의 주장을 검증한다.
 * ArrayList의 안쪽 배열(elementData)은 java.base 모듈 안의 private 필드라서,
 * --add-opens를 준 자식 JVM에서 리플렉션으로 읽는다. 걸린 시간은 어느 쪽이 빠른지만 단정한다.
 */
class ArrayAndLinkedListTest {

    private static final String LESSON = "array-and-linked-list";
    private static final List<String> OPENS = List.of("--add-opens", "java.base/java.util=ALL-UNNAMED");

    /** 자식 JVM에서 돈다. 안쪽 배열의 길이를 읽어 한 줄씩 출력한다 */
    public static final class CapacityProbe {

        static int capacity(ArrayList<?> list) throws Exception {
            Field f = ArrayList.class.getDeclaredField("elementData");
            f.setAccessible(true);
            return ((Object[]) f.get(list)).length;
        }

        public static void main(String[] args) throws Exception {
            // 1. 빈 리스트에 1부터 16까지 넣으며 size와 용량을 본다
            ArrayList<Integer> list = new ArrayList<>();
            System.out.println("empty " + capacity(list));
            for (int i = 1; i <= 16; i++) {
                list.add(i);
                System.out.println("add " + i + " size " + list.size() + " capacity " + capacity(list));
            }
            // 2. 100만 개를 넣는 동안 배열을 새로 만든 횟수
            ArrayList<Integer> big = new ArrayList<>();
            int grows = 0;
            int last = capacity(big);
            StringBuilder lengths = new StringBuilder();
            for (int i = 1; i <= 1_000_000; i++) {
                big.add(i);
                int c = capacity(big);
                if (c != last) {
                    grows++;
                    if (grows <= 6) {
                        lengths.append(c).append(' ');
                    }
                    last = c;
                }
            }
            System.out.println("million grows " + grows + " final " + last + " first " + lengths.toString().strip());
            // 3. 용량을 주고 만든 리스트
            ArrayList<Integer> sized = new ArrayList<>(100);
            int before = capacity(sized);
            int sizedGrows = 0;
            for (int i = 0; i < 100; i++) {
                sized.add(i);
                if (capacity(sized) != before) {
                    sizedGrows++;
                    before = capacity(sized);
                }
            }
            System.out.println("sized100 grows " + sizedGrows + " capacity " + capacity(sized));
        }
    }

    @Test
    @DisplayName("빈 ArrayList는 처음 add에서 10칸, 11번째에서 15칸, 16번째에서 22칸이 된다. 100만 개까지는 서른 번 남짓 늘어난다")
    void arrayListGrowth() {
        ChildJvm.Run r = ChildJvm.run(OPENS, CapacityProbe.class);
        assertThat(r.exitCode()).as(r.stderr()).isZero();
        List<String> lines = r.stdout().lines().toList();

        assertThat(lines).contains(
                "empty 0",
                "add 1 size 1 capacity 10",
                "add 10 size 10 capacity 10",
                "add 11 size 11 capacity 15",
                "add 15 size 15 capacity 15",
                "add 16 size 16 capacity 22",
                "sized100 grows 0 capacity 100");
        String million = lines.stream().filter(l -> l.startsWith("million")).findFirst().orElseThrow();
        int grows = Integer.parseInt(million.split(" ")[2]);
        // 처음 10칸을 만드는 것까지 센 횟수. 10 × 1.5^k가 100만을 넘는 k는 29
        assertThat(grows).isBetween(28, 32);

        StringBuilder steps = new StringBuilder();
        for (String l : lines) {
            if (l.startsWith("add ") && (l.contains("add 1 ") || l.contains("add 10 ") || l.contains("add 11 ")
                    || l.contains("add 16 "))) {
                String[] p = l.split(" ");
                steps.append("%s번째 add 뒤: size %s, 배열 길이 %s\n".formatted(p[1], p[3], p[5]));
            }
        }
        CiOutput.record(LESSON, "predict", "빈 리스트의 배열 길이: 0\n" + steps.toString().strip());
        String[] m = million.split(" ");
        CiOutput.record(LESSON, "million", """
                100만 개를 넣는 동안
                배열을 새로 만든 횟수: %s번
                처음 길이들: %s ...
                마지막 배열 길이: %s"""
                .formatted(m[2], million.substring(million.indexOf("first ") + 6).replace(" ", ", "), m[4]));
        CiOutput.record(LESSON, "presize", "new ArrayList<>(100)에 100개를 넣는 동안\n배열을 새로 만든 횟수: 0번, 길이: 100");
    }

    @Test
    @DisplayName("다섯 칸 리스트의 1번 자리에 99를 넣으면 [1, 99, 2, 3, 4, 5]다. LinkedList도 결과는 같다")
    void insertInMiddle() {
        List<Integer> tickets = new ArrayList<>(List.of(1, 2, 3, 4, 5));
        tickets.add(1, 99);
        List<Integer> linked = new LinkedList<>(List.of(1, 2, 3, 4, 5));
        linked.add(1, 99);

        assertThat(tickets).containsExactly(1, 99, 2, 3, 4, 5);
        assertThat(linked).isEqualTo(tickets);
        String printed = tickets.toString();
        tickets.remove(1);
        assertThat(tickets).containsExactly(1, 2, 3, 4, 5);
        CiOutput.record(LESSON, "insert", printed);
    }

    static long millis(Runnable r) {
        long start = System.nanoTime();
        r.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    static long timeAddFirst(Supplier<List<Integer>> factory, int n) {
        List<Integer> list = factory.get();
        return millis(() -> {
            for (int i = 0; i < n; i++) {
                list.add(0, i);
            }
        });
    }

    static long timeAddLast(Supplier<List<Integer>> factory, int n) {
        List<Integer> list = factory.get();
        return millis(() -> {
            for (int i = 0; i < n; i++) {
                list.add(i);
            }
        });
    }

    static long timeIterate(List<Integer> list) {
        long[] sum = {0};
        long ms = millis(() -> {
            for (int x : list) {
                sum[0] += x;
            }
        });
        assertThat(sum[0]).isPositive();
        return ms;
    }

    static long timeGet(List<Integer> list, int times) {
        long[] sum = {0};
        int n = list.size();
        long ms = millis(() -> {
            for (int i = 0; i < times; i++) {
                sum[0] += list.get((int) ((i * 7_919L) % n));
            }
        });
        assertThat(sum[0]).isPositive();
        return ms;
    }

    static List<Integer> filled(Supplier<List<Integer>> factory, int n) {
        List<Integer> list = factory.get();
        for (int i = 1; i <= n; i++) {
            list.add(i);
        }
        return list;
    }

    @Test
    @DisplayName("맨 앞에 넣기는 LinkedList가 빠르고 get(i)는 ArrayList가 빠르다. 맨 뒤에 넣기와 차례로 읽기는 남기기만 한다")
    void compareOperations() {
        Supplier<List<Integer>> array = ArrayList::new;
        Supplier<List<Integer>> linked = LinkedList::new;
        // JIT가 데우도록 작은 입력으로 몇 번 돌린다
        for (int i = 0; i < 5; i++) {
            timeAddFirst(array, 5_000);
            timeAddFirst(linked, 5_000);
            timeIterate(filled(array, 100_000));
            timeIterate(filled(linked, 100_000));
        }

        long frontArray = timeAddFirst(array, 100_000);
        long frontLinked = timeAddFirst(linked, 100_000);
        long lastArray = timeAddLast(array, 1_000_000);
        long lastLinked = timeAddLast(linked, 1_000_000);
        List<Integer> a = filled(array, 1_000_000);
        List<Integer> l = filled(linked, 1_000_000);
        long iterArray = timeIterate(a);
        long iterLinked = timeIterate(l);
        List<Integer> a2 = filled(array, 100_000);
        List<Integer> l2 = filled(linked, 100_000);
        long getArray = timeGet(a2, 10_000);
        long getLinked = timeGet(l2, 10_000);

        assertThat(frontLinked).isLessThan(frontArray);
        assertThat(getArray).isLessThan(getLinked);
        // 맨 뒤에 넣기와 차례로 읽기는 둘 다 같은 복잡도라 실행마다 앞서는 쪽이 바뀐다 (CI에서 3ms와 10ms로 뒤집힌 적이 있다)

        CiOutput.record(LESSON, "compare", """
                맨 앞에 넣기 10만 번
                  ArrayList %,dms, LinkedList %,dms
                맨 뒤에 넣기 100만 번
                  ArrayList %,dms, LinkedList %,dms
                100만 개를 처음부터 끝까지 읽기
                  ArrayList %,dms, LinkedList %,dms
                원소 10만 개에서 get(i) 1만 번
                  ArrayList %,dms, LinkedList %,dms"""
                .formatted(frontArray, frontLinked, lastArray, lastLinked, iterArray, iterLinked,
                        getArray, getLinked));
    }

    @Test
    @DisplayName("LinkedList를 get(i)로 돌면 향상된 for보다 훨씬 오래 걸린다")
    void linkedListIndexLoop() {
        List<Long> ids = new LinkedList<>();
        for (long i = 1; i <= 50_000; i++) {
            ids.add(i);
        }
        long[] sums = new long[2];
        long byIndex = millis(() -> {
            for (int i = 0; i < ids.size(); i++) {
                sums[0] += ids.get(i);
            }
        });
        long byForEach = millis(() -> {
            for (Long id : ids) {
                sums[1] += id;
            }
        });
        assertThat(sums[0]).isEqualTo(sums[1]);
        assertThat(byForEach).isLessThan(byIndex);
        CiOutput.record(LESSON, "getloop", """
                LinkedList, 원소 50,000개
                get(i)로 돌기: %,dms
                향상된 for로 돌기: %,dms""".formatted(byIndex, byForEach));
    }

    @Test
    @DisplayName("Arrays.asList가 돌려준 리스트에 add하면 UnsupportedOperationException이 난다")
    void arraysAsListIsFixedSize() {
        List<Integer> fixed = Arrays.asList(1, 2, 3);
        assertThatThrownBy(() -> fixed.add(4)).isInstanceOf(UnsupportedOperationException.class);
        fixed.set(0, 9);
        assertThat(fixed).containsExactly(9, 2, 3);
        CiOutput.record(LESSON, "aslist", "Arrays.asList(1, 2, 3).add(4)\n→ UnsupportedOperationException");
    }
}
