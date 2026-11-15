package lab.devlessons.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 레슨 "스레드 풀"의 예제를 실제 JVM에서 돌리고, 출력을 레슨 페이지에 공개한다 (CiOutput) */
class ThreadPoolOutputTest {

    @Test
    @DisplayName("0장 예제: core 2, max 4, 큐 2인 풀에 작업 7개를 넣을 때마다 스레드와 큐 수 (출력 공개)")
    void predictExample() throws Exception {
        StringBuilder out = new StringBuilder();
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
        List<Integer> threads = new ArrayList<>();
        try {
            for (int i = 1; i <= 7; i++) {
                try {
                    pool.execute(() -> {
                        try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    });
                    threads.add(pool.getPoolSize());
                    out.append("작업 ").append(i).append(" 제출 → 스레드 ").append(pool.getPoolSize())
                            .append("개, 큐 ").append(pool.getQueue().size()).append("개\n");
                } catch (RejectedExecutionException e) {
                    out.append("작업 ").append(i).append(" 거절 (RejectedExecutionException)\n");
                }
            }
        } finally {
            release.countDown();
            pool.shutdown();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
        out.append("완료된 작업: ").append(pool.getCompletedTaskCount());

        assertThat(threads).containsExactly(1, 2, 2, 2, 3, 4);
        CiOutput.record("thread-pool", "predict", out.toString());
    }

    @Test
    @DisplayName("200ms 작업 8개를 스레드 1개, 2개, 4개, 8개 풀로 실행한 실제 시간 (출력 공개)")
    void measuredTiming() throws Exception {
        StringBuilder out = new StringBuilder();
        long[] measured = new long[4];
        int[] sizes = {1, 2, 4, 8};
        for (int k = 0; k < sizes.length; k++) {
            ExecutorService pool = Executors.newFixedThreadPool(sizes[k]);
            long start = System.nanoTime();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    Thread.sleep(200);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
            measured[k] = (System.nanoTime() - start) / 1_000_000;
            pool.shutdown();
            out.append("스레드 ").append(sizes[k]).append("개: ").append(measured[k]).append("ms\n");
        }
        out.append("(200ms 작업 8개, 기다리는 작업이라 스레드가 많을수록 빨라진다)");

        assertThat(measured[0]).isGreaterThanOrEqualTo(1600);
        assertThat(measured[3]).isLessThan(measured[0]);
        CiOutput.record("thread-pool", "timing", out.toString());
    }
}
