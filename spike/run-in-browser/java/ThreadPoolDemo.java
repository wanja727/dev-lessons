import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** 스레드 풀 레슨 0장 예제를 실제로 돌려 보는 데모. 결과가 결정적이도록 작업은 신호를 받을 때까지 기다린다 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("java.version=" + System.getProperty("java.version"));

        // 1. 코어 2, 최대 4, 큐 2짜리 풀에 오래 걸리는 작업 7개
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
        for (int i = 1; i <= 7; i++) {
            final int n = i;
            try {
                pool.execute(() -> {
                    try { release.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
                System.out.println("작업 " + n + " 제출 → 스레드 " + pool.getPoolSize() + "개, 큐 " + pool.getQueue().size() + "개");
            } catch (RejectedExecutionException e) {
                System.out.println("작업 " + n + " 거절 (RejectedExecutionException)");
            }
        }
        release.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("완료된 작업: " + pool.getCompletedTaskCount());

        // 2. 스레드 2개로 200ms 작업 4개 → 약 400ms
        ExecutorService fixed = Executors.newFixedThreadPool(2);
        long s = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            final int n = i;
            futures.add(fixed.submit(() -> {
                Thread.sleep(200);
                return "작업" + n + "@" + Thread.currentThread().getName();
            }));
        }
        for (Future<String> f : futures) {
            System.out.println(f.get());
        }
        long ms = (System.nanoTime() - s) / 1_000_000;
        System.out.println("200ms 작업 4개, 스레드 2개: " + ms + "ms (기대 약 400ms)");
        fixed.shutdown();

        System.out.println("전체 실행 시간: " + (System.nanoTime() - t0) / 1_000_000 + "ms");
    }
}
