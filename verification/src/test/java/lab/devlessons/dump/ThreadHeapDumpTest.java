package lab.devlessons.dump;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "스레드 덤프와 힙 덤프"(T10). 자식 JVM에 데드락, 한 락에 몰린 스레드, 메모리 누수를 만들고
 * jcmd로 스레드 덤프, 클래스 히스토그램, 힙 덤프를 떠서 원인이 어떻게 보이는지 본다.
 */
class ThreadHeapDumpTest {

    static final String LESSON = "thread-heap-dump";

    @TempDir Path dir;

    static final String STUCK = """
            public class Stuck {
                static final Object A = new Object();
                static final Object B = new Object();
                static final Object STOCK = new Object();

                static void sleep(long ms) {
                    try { Thread.sleep(ms); } catch (InterruptedException e) { }
                }

                static void decrease() {
                    synchronized (STOCK) {
                        sleep(600_000); // 재고 차감 중 느린 외부 호출
                    }
                }

                public static void main(String[] args) {
                    new Thread(() -> { synchronized (A) { sleep(200); synchronized (B) { } } }, "order-1").start();
                    new Thread(() -> { synchronized (B) { sleep(200); synchronized (A) { } } }, "order-2").start();
                    new Thread(Stuck::decrease, "stock-holder").start();
                    sleep(100);
                    for (int i = 1; i <= 5; i++) {
                        new Thread(Stuck::decrease, "stock-" + i).start();
                    }
                    sleep(1000);
                    System.out.println("ready");
                    sleep(600_000);
                }
            }
            """;

    static final String LEAK = """
            import java.util.HashMap;
            import java.util.Map;

            public class Leak {
                static final Map<Long, byte[]> CACHE = new HashMap<>();

                public static void main(String[] args) throws Exception {
                    long limit = args.length > 0 ? Long.parseLong(args[0]) : Long.MAX_VALUE;
                    for (long id = 1; id <= limit; id++) {
                        CACHE.put(id, new byte[10_000]);
                        if (id % 1000 == 0) {
                            System.out.println("요청 " + id);
                            Thread.sleep(500);
                        }
                    }
                    Thread.sleep(600_000);
                }
            }
            """;

    static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    static String jcmdPath() {
        return Path.of(System.getProperty("java.home"), "bin", "jcmd").toString();
    }

    /** 단일 파일 자바 프로그램을 띄운다. 표준 출력과 오류는 하나로 합친다 */
    Process launch(String name, String source, List<String> options, String... args) throws IOException {
        Path file = dir.resolve(name + ".java");
        Files.writeString(file, source);
        List<String> cmd = new ArrayList<>();
        cmd.add(java());
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.addAll(options);
        cmd.add(file.toString());
        cmd.addAll(List.of(args));
        return new ProcessBuilder(cmd).redirectErrorStream(true).start();
    }

    static String waitFor(Process p, String marker, long seconds) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), UTF_8));
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        StringBuilder seen = new StringBuilder();
        String line;
        while (System.nanoTime() < end && (line = r.readLine()) != null) {
            seen.append(line).append('\n');
            if (line.contains(marker)) {
                return seen.toString();
            }
        }
        throw new IllegalStateException("'" + marker + "'를 보지 못했다:\n" + seen);
    }

    static String jcmd(long pid, String... command) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(jcmdPath(), Long.toString(pid)));
        cmd.addAll(List.of(command));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).isTrue();
        return out.replace("\r", "");
    }

    /** 스레드 덤프에서 이름이 name인 스레드의 덩어리(빈 줄까지) */
    static String block(String dump, String name) {
        int i = dump.indexOf("\"" + name + "\"");
        if (i < 0) {
            return "";
        }
        int j = dump.indexOf("\n\n", i);
        return dump.substring(i, j < 0 ? dump.length() : j);
    }

    /** 덩어리에서 첫 줄의 긴 꼬리(주소, nid)를 줄여 보여 주기 좋게 만든다 */
    static String shortBlock(String block) {
        return block.lines().map(l -> l.startsWith("\"")
                        ? l.replaceAll(" #\\d+ \\[\\d+\\].*", "")
                        : l.replaceAll("\\(Stuck\\.java:\\d+\\)", "(Stuck.java)"))
                .filter(l -> !l.contains("java.lang.Thread.run") && !l.contains("Thread.runWith") && !l.contains("$$Lambda"))
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("데드락에 빠진 두 스레드는 BLOCKED이고 덤프 끝에 Found one Java-level deadlock이 나온다. 한 락에 몰린 스레드들은 같은 주소를 waiting to lock한다")
    void threadDumpShowsDeadlockAndContention() throws Exception {
        Process p = launch("Stuck", STUCK, List.of());
        try {
            waitFor(p, "ready", 60);
            String dump = jcmd(p.pid(), "Thread.print");

            String o1 = block(dump, "order-1");
            String o2 = block(dump, "order-2");
            assertThat(o1).contains("java.lang.Thread.State: BLOCKED (on object monitor)");
            assertThat(o2).contains("java.lang.Thread.State: BLOCKED (on object monitor)");
            assertThat(dump).contains("Found one Java-level deadlock");

            String holder = block(dump, "stock-holder");
            Matcher locked = Pattern.compile("- locked <(0x[0-9a-f]+)>").matcher(holder);
            assertThat(locked.find()).isTrue();
            String address = locked.group(1);
            long waiting = Stream.of(1, 2, 3, 4, 5).map(i -> block(dump, "stock-" + i))
                    .filter(b -> b.contains("- waiting to lock <" + address + ">")).count();
            assertThat(holder).contains("TIMED_WAITING");
            assertThat(waiting).isEqualTo(5);

            String deadlockPart = dump.substring(dump.indexOf("Found one Java-level deadlock"));
            String summary = deadlockPart.lines().limit(9).collect(Collectors.joining("\n"));
            CiOutput.record(LESSON, "deadlock", """
                    $ jcmd <PID> Thread.print
                    ...
                    %s

                    %s
                    ...
                    %s""".formatted(shortBlock(o1), shortBlock(o2), summary.replaceAll(" \\(object 0x[0-9a-f]+, a java.lang.Object\\)", "")));
            CiOutput.record(LESSON, "contention", """
                    %s

                    %s

                    stock-1~5 가운데 <%s>를 waiting to lock → %d개""".formatted(shortBlock(holder),
                    shortBlock(block(dump, "stock-1")), address, waiting));
        } finally {
            p.destroyForcibly();
        }
    }

    static final Pattern HISTO_BYTES = Pattern.compile("^\\s*\\d+:\\s+(\\d+)\\s+(\\d+)\\s+\\[B\\b", Pattern.MULTILINE);

    static long[] byteArrays(String histogram) {
        Matcher m = HISTO_BYTES.matcher(histogram);
        assertThat(m.find()).as(histogram).isTrue();
        return new long[] {Long.parseLong(m.group(1)), Long.parseLong(m.group(2))};
    }

    static String topLines(String histogram, int n) {
        return histogram.lines().filter(l -> l.matches("^\\s*(num|\\d+:|-).*")).limit(n + 2)
                .map(l -> l.replaceAll("\\s+\\(java\\.base@[^)]*\\)", ""))
                .collect(Collectors.joining("\n"));
    }

    @Test
    @DisplayName("static 맵에 쌓는 프로그램의 클래스 히스토그램을 두 번 뜨면 byte[]가 늘고, GC.heap_dump는 파일을 남긴다")
    void histogramShowsGrowingClass() throws Exception {
        Process p = launch("Leak", LEAK, List.of("-Xmx512m"));
        try {
            waitFor(p, "요청 2000", 60);
            String first = jcmd(p.pid(), "GC.class_histogram");
            waitFor(p, "요청 6000", 60);
            String second = jcmd(p.pid(), "GC.class_histogram");
            Path hprof = dir.resolve("leak.hprof");
            String dumpOut = jcmd(p.pid(), "GC.heap_dump", hprof.toString()).replace(hprof.toString(), "/tmp/leak.hprof");

            long[] a = byteArrays(first);
            long[] b = byteArrays(second);
            // 자식 JVM은 jcmd가 도는 동안에도 앞으로 가서, 두 장 사이에 늘어난 수는 실행마다 다르다
            assertThat(b[0]).isGreaterThan(a[0] + 1000);
            assertThat(b[1]).isGreaterThan(a[1] + 10_000_000L);
            assertThat(Files.size(hprof)).isGreaterThan(0);
            CiOutput.record(LESSON, "histogram", """
                    $ jcmd <PID> GC.class_histogram   (요청 2000번 뒤)
                    %s

                    $ jcmd <PID> GC.class_histogram   (요청 6000번 뒤)
                    %s

                    byte[] 객체 수 → %,d개에서 %,d개
                    byte[] 크기 → %,d바이트에서 %,d바이트""".formatted(topLines(first, 3), topLines(second, 3),
                    a[0], b[0], a[1], b[1]));
            CiOutput.record(LESSON, "heapdump", """
                    $ jcmd <PID> GC.heap_dump /tmp/leak.hprof
                    %s
                    파일 크기 → %,d바이트""".formatted(dumpOut.lines().skip(1).collect(Collectors.joining("\n")).strip(),
                    Files.size(hprof)));
        } finally {
            p.destroyForcibly();
        }
    }

    @Test
    @DisplayName("HeapDumpOnOutOfMemoryError를 준 작은 힙의 JVM은 OOM 때 hprof 파일을 남기고, GC 로그에서 GC 뒤의 힙 크기가 오른다")
    void heapDumpOnOom() throws Exception {
        Path dumps = Files.createDirectories(dir.resolve("dumps"));
        Process p = launch("Leak", LEAK, List.of("-Xmx64m", "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:HeapDumpPath=" + dumps, "-Xlog:gc"));
        String out = new String(p.getInputStream().readAllBytes(), UTF_8).replace("\r", "");
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).isTrue();

        List<Path> files;
        try (Stream<Path> s = Files.list(dumps)) {
            files = s.toList();
        }
        assertThat(out).contains("java.lang.OutOfMemoryError").contains("Dumping heap to");
        assertThat(files).hasSize(1);
        assertThat(files.get(0).toString()).endsWith(".hprof");

        List<String> gcLines = out.lines().filter(l -> l.contains("Pause Young") || l.contains("Pause Full"))
                .map(l -> l.replaceAll("^\\[[^]]*\\]\\[info\\]\\[gc\\s*\\] ", "")).toList();
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < gcLines.size(); i += Math.max(1, gcLines.size() / 5)) {
            picked.add(gcLines.get(i));
        }
        String dumping = out.lines().filter(l -> l.contains("Dumping heap to") || l.contains("Heap dump file created")
                || l.contains("OutOfMemoryError")).limit(3)
                .map(l -> l.replace(dumps.toString(), "/var/dumps").replace("\\", "/")).collect(Collectors.joining("\n"));
        CiOutput.record(LESSON, "oom", """
                $ java -Xmx64m -XX:+HeapDumpOnOutOfMemoryError \\
                    -XX:HeapDumpPath=/var/dumps -Xlog:gc Leak.java
                (GC 로그 가운데 몇 줄. 화살표 오른쪽이 GC 뒤의 힙)
                %s
                ...
                %s

                /var/dumps → %s""".formatted(String.join("\n", picked), dumping,
                files.get(0).getFileName().toString().replaceAll("\\d+", "<PID>")));
    }
}
