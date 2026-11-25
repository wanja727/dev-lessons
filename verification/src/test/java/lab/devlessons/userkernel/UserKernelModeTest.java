package lab.devlessons.userkernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "사용자 모드와 커널 모드"(C11)의 주장을 검증한다. 시스템 콜 수를 직접 세지는 않고,
 * 같은 일을 시스템 콜을 많이 부르는 방법과 적게 부르는 방법으로 해서 시간을 비교한다.
 */
class UserKernelModeTest {

    private static final String LESSON = "user-kernel-mode";

    @TempDir
    Path dir;

    static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    @Test
    @DisplayName("1MB 파일을 버퍼 없이 한 바이트씩 읽는 시간은 BufferedInputStream으로 읽는 시간의 세 배가 넘는다")
    void bufferedReadIsFaster() throws Exception {
        Path f = dir.resolve("orders.csv");
        byte[] data = new byte[1024 * 1024];
        ThreadLocalRandom.current().nextBytes(data);
        Files.write(f, data);

        long t0 = System.nanoTime();
        long count1 = 0;
        try (var in = new FileInputStream(f.toFile())) {
            while (in.read() != -1) {
                count1++;
            }
        }
        long unbuffered = ms(t0);

        long t1 = System.nanoTime();
        long count2 = 0;
        try (var in = new BufferedInputStream(new FileInputStream(f.toFile()))) {
            while (in.read() != -1) {
                count2++;
            }
        }
        long buffered = ms(t1);

        assertThat(count1).isEqualTo(data.length);
        assertThat(count2).isEqualTo(data.length);
        assertThat(unbuffered).isGreaterThan(buffered * 3);
        CiOutput.record(LESSON, "predict", "1MB 파일을 read()로 한 바이트씩\nFileInputStream만        → " + String.format("%,dms", unbuffered)
                + " (read 시스템 콜 약 100만 번)\nBufferedInputStream으로 감쌈 → " + String.format("%,dms", buffered)
                + " (8KB마다 한 번, 약 130번)");
    }

    static byte[] line(int i) {
        return ("주문 " + i + " 결제 완료\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("10만 줄을 줄마다 FileOutputStream.write로 쓰는 시간이 BufferedWriter로 쓰는 시간보다 길다")
    void bufferedWriteIsFaster() throws Exception {
        Path a = dir.resolve("a.log");
        Path b = dir.resolve("b.log");

        long t0 = System.nanoTime();
        try (var out = new FileOutputStream(a.toFile())) {
            for (int i = 0; i < 100_000; i++) {
                out.write(line(i));
            }
        }
        long unbuffered = ms(t0);

        long t1 = System.nanoTime();
        try (var out = new BufferedWriter(new FileWriter(b.toFile(), StandardCharsets.UTF_8))) {
            for (int i = 0; i < 100_000; i++) {
                out.write("주문 " + i + " 결제 완료\n");
            }
        }
        long buffered = ms(t1);

        assertThat(Files.size(a)).isEqualTo(Files.size(b));
        assertThat(unbuffered).isGreaterThan(buffered);
        CiOutput.record(LESSON, "write", "10만 줄 쓰기\n줄마다 FileOutputStream.write → " + String.format("%,dms", unbuffered)
                + "\nBufferedWriter               → " + String.format("%,dms", buffered));
    }

    @Test
    @DisplayName("BufferedWriter로 쓰고 flush나 close 없이 두면 파일에 아직 내용이 없고, close하면 나간다")
    void unflushedBufferStaysInMemory() throws Exception {
        Path f = dir.resolve("app.log");
        var out = new BufferedWriter(new FileWriter(f.toFile(), StandardCharsets.UTF_8));
        for (int i = 0; i < 10; i++) {
            out.write("주문 " + i + " 결제 완료\n");
        }
        long before = Files.size(f);
        out.close();
        long after = Files.size(f);
        assertThat(before).isZero();
        assertThat(after).isGreaterThan(0);
        CiOutput.record(LESSON, "flush", "BufferedWriter로 10줄 write\nclose 전 파일 크기 → " + before + "바이트\nclose 뒤 파일 크기 → " + after
                + "바이트");
    }
}
