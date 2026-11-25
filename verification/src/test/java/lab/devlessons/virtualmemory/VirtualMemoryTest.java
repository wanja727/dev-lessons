package lab.devlessons.virtualmemory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "가상 메모리"(C10)의 주장을 검증한다. /proc/self/status를 읽으므로 리눅스에서만 돈다(CI). 다른 운영체제에서는 건너뛴다.
 */
class VirtualMemoryTest {

    private static final String LESSON = "virtual-memory";

    /** 자식 JVM에서 실행된다. 힙을 거의 쓰지 않고 자기 메모리 두 숫자를 출력한다. */
    public static class Status {
        public static void main(String[] args) throws Exception {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmSize") || line.startsWith("VmRSS")) {
                    System.out.println(line.replaceAll("\\s+", " "));
                }
            }
        }
    }

    static long kb(String out, String name) {
        Matcher m = Pattern.compile(name + ": (\\d+) kB").matcher(out);
        assertThat(m.find()).as(out).isTrue();
        return Long.parseLong(m.group(1));
    }

    static String mb(long kb) {
        return String.format("%,dMB", kb / 1024);
    }

    @Test
    @DisplayName("힙을 512MB로 준 JVM이 힙을 거의 쓰지 않으면 VmSize는 VmRSS보다 훨씬 크고, AlwaysPreTouch를 켜면 VmRSS가 512MB를 넘는다")
    void reservedVersusTouched() {
        assumeTrue(Files.exists(Path.of("/proc/self/status")), "리눅스에서만 확인한다");

        ChildJvm.Run lazy = ChildJvm.run(List.of("-Xms512m", "-Xmx512m"), Status.class);
        ChildJvm.Run touched = ChildJvm.run(List.of("-Xms512m", "-Xmx512m", "-XX:+AlwaysPreTouch"), Status.class);

        long lazySize = kb(lazy.stdout(), "VmSize");
        long lazyRss = kb(lazy.stdout(), "VmRSS");
        long touchedRss = kb(touched.stdout(), "VmRSS");

        assertThat(lazySize).isGreaterThan(lazyRss * 3);
        assertThat(lazyRss).isLessThan(512 * 1024);
        assertThat(touchedRss).isGreaterThan(512 * 1024);

        CiOutput.record(LESSON, "predict", "java -Xms512m -Xmx512m (힙을 거의 쓰지 않음)\nVmSize → " + mb(lazySize)
                + "\nVmRSS  → " + mb(lazyRss));
        CiOutput.record(LESSON, "pretouch", "java -Xms512m -Xmx512m -XX:+AlwaysPreTouch\nVmRSS → " + mb(touchedRss)
                + " (켜지 않았을 때 " + mb(lazyRss) + ")");
    }
}
