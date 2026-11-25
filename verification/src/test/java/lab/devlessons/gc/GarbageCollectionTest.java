package lab.devlessons.gc;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "가비지 컬렉션"(J15)의 주장을 검증한다.
 * 객체가 치워졌는지는 WeakReference로 본다. System.gc()는 명세상 보장이 없어서, 비워질 때까지 정해진 시간 동안 거듭 부른다.
 * 수집기와 GC 로그는 옵션을 준 자식 JVM에서 본다.
 */
class GarbageCollectionTest {

    private static final String LESSON = "garbage-collection";

    static class Order {
        Order partner;
        final byte[] payload = new byte[1024];
    }

    /** 약한 참조가 비워질 때까지 GC를 요청한다. 5초 안에 비워지면 true */
    static boolean clearedWithinGc(WeakReference<?> ref) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (ref.get() == null) {
                return true;
            }
            System.gc();
            Thread.sleep(100);
        }
        return ref.get() == null;
    }

    // 코드 predict: 서로를 가리키는 두 주문
    static WeakReference<?>[] makeCycle() {
        Order a = new Order();
        Order b = new Order();
        a.partner = b;
        b.partner = a;
        return new WeakReference<?>[] {new WeakReference<>(a), new WeakReference<>(b)};
        // 메서드가 끝나면 a와 b가 사라진다. 두 객체는 서로만 가리킨다
    }

    @Test
    @DisplayName("서로를 가리키는 두 객체도 루트에서 닿지 않으면 치워진다")
    void cycleCollected() throws Exception {
        WeakReference<?>[] refs = makeCycle();
        boolean a = clearedWithinGc(refs[0]);
        boolean b = clearedWithinGc(refs[1]);
        assertThat(a).isTrue();
        assertThat(b).isTrue();
        CiOutput.record(LESSON, "predict", """
                a.partner = b; b.partner = a; 뒤 두 변수가 사라짐
                System.gc()를 요청한 뒤
                a가 가리키던 주문 → %s
                b가 가리키던 주문 → %s""".formatted(a ? "치워짐" : "남음", b ? "치워짐" : "남음"));
    }

    // 코드 roots
    static List<Order> history = new ArrayList<>();
    static WeakReference<Order> tempRef;
    static WeakReference<Order> savedRef;

    static void handle() {
        Order temp = new Order();
        Order saved = new Order();
        history.add(saved);
        tempRef = new WeakReference<>(temp);
        savedRef = new WeakReference<>(saved);
    }

    @Test
    @DisplayName("메서드가 끝나면 지역 변수만 가리키던 객체는 치워지고, static 리스트에 든 객체는 남는다")
    void rootsDecide() throws Exception {
        history.clear();
        handle();
        boolean tempCleared = clearedWithinGc(tempRef);
        // temp가 치워졌다면 GC가 돈 것이다. 같은 GC를 거친 saved는 남아 있어야 한다
        System.gc();
        boolean savedCleared = savedRef.get() == null;

        assertThat(tempCleared).isTrue();
        assertThat(savedCleared).isFalse();
        CiOutput.record(LESSON, "roots", """
                handle()이 끝나고 GC를 요청한 뒤
                temp가 가리키던 객체  → %s
                saved가 가리키던 객체 → %s (history.size() = %d)""".formatted(tempCleared ? "치워짐" : "남음",
                savedCleared ? "치워짐" : "남음", history.size()));
        history.clear();
    }

    /** 자식 JVM: 금방 버리는 객체를 많이 만들고 수집기별 횟수를 앞뒤로 출력한다 */
    public static class Churn {
        static volatile Object sink;

        public static void main(String[] args) {
            System.out.println("전 " + counts());
            for (int i = 0; i < 2_000_000; i++) {
                sink = new byte[1024];
            }
            System.out.println("후 " + counts());
        }

        static String counts() {
            StringBuilder s = new StringBuilder();
            for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (!s.isEmpty()) {
                    s.append(", ");
                }
                s.append(gc.getName()).append('=').append(gc.getCollectionCount());
            }
            return s.toString();
        }
    }

    static long count(String line, String name) {
        Matcher m = Pattern.compile(Pattern.quote(name) + "=(\\d+)").matcher(line);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    @Test
    @DisplayName("금방 버리는 객체를 많이 만들면 젊은 세대의 수집 횟수가 늘어난다")
    void youngCollections() {
        ChildJvm.Run r = ChildJvm.run(List.of("-XX:+UseG1GC", "-Xmx64m"), Churn.class);
        assertThat(r.exitCode()).isZero();
        List<String> lines = r.stdout().strip().lines().toList();
        long before = count(lines.get(0), "G1 Young Generation");
        long after = count(lines.get(1), "G1 Young Generation");
        assertThat(after).isGreaterThan(before);
        CiOutput.record(LESSON, "young-count", """
                java -XX:+UseG1GC -Xmx64m Churn
                (1KB 배열 200만 개를 만들고 바로 버림, 약 2GB)
                %s
                %s""".formatted(lines.get(0), lines.get(1)));
    }

    /** 자식 JVM: 아무것도 하지 않는다. -Xlog:gc의 첫 줄만 본다 */
    public static class Noop {
        public static void main(String[] args) {
        }
    }

    static String usingLine(String out) {
        return out.lines().filter(l -> l.contains("Using ")).findFirst().orElse("(없음)");
    }

    @Test
    @DisplayName("-XX:+UseSerialGC와 -XX:+UseG1GC로 띄우면 각각 그 수집기가 쓰이고, 옵션이 없으면 JVM이 고른다")
    void collectorChoice() {
        String serial = usingLine(ChildJvm.run(List.of("-XX:+UseSerialGC", "-Xlog:gc"), Noop.class).stdout());
        String g1 = usingLine(ChildJvm.run(List.of("-XX:+UseG1GC", "-Xlog:gc"), Noop.class).stdout());
        String none = usingLine(ChildJvm.run(List.of("-Xlog:gc"), Noop.class).stdout());
        int cpus = Runtime.getRuntime().availableProcessors();

        assertThat(serial).contains("Using Serial");
        assertThat(g1).contains("Using G1");
        assertThat(none).contains("Using ");
        CiOutput.record(LESSON, "collector", """
                java -XX:+UseSerialGC -Xlog:gc → %s
                java -XX:+UseG1GC -Xlog:gc    → %s
                java -Xlog:gc (옵션 없음)      → %s
                (이 기계의 CPU %d개)""".formatted(strip(serial), strip(g1), strip(none), cpus));
    }

    /** "[0.004s][info][gc] Using G1"에서 앞의 시각과 태그를 뗀다 */
    static String strip(String line) {
        return line.replaceAll("^(\\[[^]]*])+\\s*", "");
    }

    static final Pattern PAUSE = Pattern.compile(
            "GC\\(\\d+\\) Pause Young .* (\\d+)M->(\\d+)M\\((\\d+)M\\) ([\\d.]+)ms");

    @Test
    @DisplayName("-Xlog:gc의 수집 한 줄에는 수집 전 사용량, 수집 뒤 사용량, 힙 크기, 걸린 시간이 나온다")
    void gcLogLine() {
        ChildJvm.Run r = ChildJvm.run(List.of("-XX:+UseG1GC", "-Xmx64m", "-Xlog:gc"), Churn.class);
        String line = r.stdout().lines().filter(l -> PAUSE.matcher(l).find()).findFirst().orElse("");
        Matcher m = PAUSE.matcher(line);

        assertThat(m.find()).isTrue();
        long beforeMb = Long.parseLong(m.group(1));
        long afterMb = Long.parseLong(m.group(2));
        assertThat(afterMb).isLessThanOrEqualTo(beforeMb);
        CiOutput.record(LESSON, "gc-log", "java -XX:+UseG1GC -Xmx64m -Xlog:gc Churn\n"
                + usingLine(r.stdout()) + "\n" + line + "\n...");
    }

    // 코드 leak
    record Rate(String key, byte[] data) {
    }

    static class RateCache {
        private final Map<String, Rate> cache = new HashMap<>();

        Rate get(String key) {
            return cache.computeIfAbsent(key, k -> load(k));
        }

        int size() {
            return cache.size();
        }

        private Rate load(String key) {
            return new Rate(key, new byte[16]);
        }
    }

    static RateCache rateCache;

    @Test
    @DisplayName("지우지 않는 맵에 서로 다른 키를 넣으면 넣은 수만큼 커지고, 다시 쓰지 않는 값도 GC 뒤에 남는다")
    void leakingMap() throws Exception {
        rateCache = new RateCache();
        WeakReference<Rate> first = new WeakReference<>(rateCache.get("user-0"));
        for (int i = 1; i < 100_000; i++) {
            rateCache.get("user-" + i);
        }
        // 비교용: 맵에 넣지 않은 값은 치워진다
        WeakReference<Rate> loose = new WeakReference<>(new Rate("loose", new byte[16]));
        boolean looseCleared = clearedWithinGc(loose);
        boolean firstCleared = first.get() == null;

        assertThat(rateCache.size()).isEqualTo(100_000);
        assertThat(looseCleared).isTrue();
        assertThat(firstCleared).isFalse();
        CiOutput.record(LESSON, "leak", """
                서로 다른 키 100,000개로 get을 부름
                cache.size() → %,d
                GC를 요청한 뒤
                맵에 넣지 않은 Rate  → %s
                "user-0"의 Rate      → %s (다시 쓰지 않지만 맵이 가리킴)""".formatted(rateCache.size(),
                looseCleared ? "치워짐" : "남음", firstCleared ? "치워짐" : "남음"));
        rateCache = null;
    }

    @Test
    @DisplayName("최대 크기를 준 Caffeine 캐시는 서로 다른 키를 계속 넣어도 그 크기를 넘지 않는다")
    void boundedCache() {
        Cache<String, Rate> cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .executor(Runnable::run)
                .build();
        for (int i = 0; i < 100_000; i++) {
            cache.get("user-" + i, k -> new Rate(k, new byte[16]));
        }
        cache.cleanUp();
        long size = cache.estimatedSize();
        assertThat(size).isLessThanOrEqualTo(10_000);
        CiOutput.record(LESSON, "bounded", """
                maximumSize(10_000)인 Caffeine 캐시에
                서로 다른 키 100,000개를 넣음
                cache.estimatedSize() → %,d""".formatted(size));
    }
}
