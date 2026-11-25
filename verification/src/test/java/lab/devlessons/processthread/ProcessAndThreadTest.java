package lab.devlessons.processthread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.devlessons.support.ChildJvm;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 레슨 "프로세스와 스레드"(C1)의 주장을 검증한다.
 * 결과가 실행마다 달라지는 경쟁 상태는 "생길 수 있다"만 단정한다(여러 번 돌려 한 번이라도 생기는지).
 */
class ProcessAndThreadTest {

    private static final String LESSON = "process-and-thread";

    static int count;

    /** 자식 JVM에서 실행된다. 자기 프로세스의 static 변수를 바꾸고 PID와 함께 출력한다. */
    public static class Child {
        public static void main(String[] args) {
            count = 100;
            System.out.println("pid=" + ProcessHandle.current().pid() + " count=" + count);
        }
    }

    @Test
    @DisplayName("자식 JVM이 static 변수를 바꿔도 부모의 같은 변수는 그대로이고 PID가 다르다")
    void processesDoNotShareMemory() {
        count = 0;
        ChildJvm.Run run = ChildJvm.run(List.of(), Child.class);
        Matcher m = Pattern.compile("pid=(\\d+) count=(\\d+)").matcher(run.stdout());
        assertThat(m.find()).as(run.stdout() + run.stderr()).isTrue();
        long parentPid = ProcessHandle.current().pid();
        assertThat(Long.parseLong(m.group(1))).isNotEqualTo(parentPid);
        assertThat(m.group(2)).isEqualTo("100");
        assertThat(count).isZero();
        CiOutput.record(LESSON, "child", "자식 JVM: pid=" + m.group(1) + ", count=" + m.group(2)
                + "\n부모 JVM: pid=" + parentPid + ", count=" + count);
    }

    @Test
    @DisplayName("다른 스레드가 static 변수에 쓴 값을 join 뒤 main 스레드가 그대로 읽는다")
    void threadsShareHeap() throws Exception {
        count = 0;
        Thread a = new Thread(() -> count = 100);
        a.start();
        a.join();
        assertThat(count).isEqualTo(100);
        CiOutput.record(LESSON, "shared", "count → " + count);
    }

    static int countLocal() {
        int n = 0;
        for (int i = 0; i < 10_000; i++) {
            n++;
        }
        return n;
    }

    @Test
    @DisplayName("같은 메서드를 두 스레드가 동시에 실행해도 지역 변수는 스레드마다 따로다")
    void localsArePerThread() throws Exception {
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        Thread t1 = new Thread(() -> {
            await(start);
            a.set(countLocal());
        });
        Thread t2 = new Thread(() -> {
            await(start);
            b.set(countLocal());
        });
        t1.start();
        t2.start();
        start.countDown();
        t1.join();
        t2.join();
        assertThat(a.get()).isEqualTo(10_000);
        assertThat(b.get()).isEqualTo(10_000);
    }

    @Test
    @DisplayName("run()은 부른 스레드에서, start()는 새 스레드에서 실행된다")
    void startVersusRun() throws Exception {
        List<String> names = Collections.synchronizedList(new ArrayList<>());
        Runnable job = () -> names.add(Thread.currentThread().getName());
        String original = Thread.currentThread().getName();
        Thread.currentThread().setName("main");
        try {
            new Thread(job, "worker").run();
        } finally {
            Thread.currentThread().setName(original);
        }
        Thread w = new Thread(job, "worker");
        w.start();
        w.join();
        assertThat(names).containsExactly("main", "worker");
        CiOutput.record(LESSON, "start-run", "run()   → " + names.get(0) + "\nstart() → " + names.get(1));
    }

    @Test
    @DisplayName("같은 스레드에 start()를 두 번 부르면 IllegalThreadStateException이다")
    void startTwice() throws Exception {
        Thread t = new Thread(() -> { });
        t.start();
        t.join();
        assertThatThrownBy(t::start).isInstanceOf(IllegalThreadStateException.class);
    }

    static int runRace() throws InterruptedException {
        count = 0;
        Runnable inc = () -> {
            for (int i = 0; i < 1_000_000; i++) {
                count++;
            }
        };
        Thread a = new Thread(inc);
        Thread b = new Thread(inc);
        a.start();
        b.start();
        a.join();
        b.join();
        return count;
    }

    @Test
    @DisplayName("동기화 없이 두 스레드가 count++를 100만 번씩 하면 2000000보다 작은 값이 나올 수 있다")
    void lostUpdate() throws Exception {
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            results.add(runRace());
            if (results.size() >= 3 && results.stream().anyMatch(v -> v < 2_000_000)) {
                break;
            }
        }
        assertThat(results).anyMatch(v -> v < 2_000_000);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            out.append(i + 1).append("번째 실행 → ").append(String.format("%,d", results.get(i))).append('\n');
        }
        CiOutput.record(LESSON, "predict", out.toString().trim());
    }

    @Test
    @DisplayName("AtomicInteger로 바꾸면 정확히 2000000이다")
    void atomicIsExact() throws Exception {
        AtomicInteger c = new AtomicInteger();
        Runnable inc = () -> {
            for (int i = 0; i < 1_000_000; i++) {
                c.incrementAndGet();
            }
        };
        Thread a = new Thread(inc);
        Thread b = new Thread(inc);
        a.start();
        b.start();
        a.join();
        b.join();
        assertThat(c.get()).isEqualTo(2_000_000);
        CiOutput.record(LESSON, "atomic", "AtomicInteger → " + String.format("%,d", c.get()));
    }

    static final CountDownLatch BOTH_ARRIVED = new CountDownLatch(2);

    @RestController
    static class ThreadNameController {
        @GetMapping("/whoami")
        String whoami() throws InterruptedException {
            BOTH_ARRIVED.countDown();
            // 다른 요청도 들어올 때까지 기다린다. 둘이 같은 순간에 처리 중이라는 뜻이다
            BOTH_ARRIVED.await(10, TimeUnit.SECONDS);
            return Thread.currentThread().getName();
        }
    }

    @Test
    @DisplayName("스프링 MVC에서 동시에 들어온 두 요청은 서로 다른 톰캣 스레드가 처리한다")
    void requestsRunOnTomcatThreads() throws Exception {
        try (MiniWeb web = MiniWeb.start(ThreadNameController.class)) {
            AtomicReference<String> first = new AtomicReference<>();
            AtomicReference<String> second = new AtomicReference<>();
            Thread a = new Thread(() -> first.set(web.get("/whoami").body()));
            Thread b = new Thread(() -> second.set(web.get("/whoami").body()));
            a.start();
            b.start();
            a.join();
            b.join();
            assertThat(first.get()).startsWith("http-nio-").contains("-exec-");
            assertThat(second.get()).startsWith("http-nio-").contains("-exec-");
            assertThat(first.get()).isNotEqualTo(second.get());
            CiOutput.record(LESSON, "tomcat", "요청 1을 처리한 스레드 → " + first.get()
                    + "\n요청 2를 처리한 스레드 → " + second.get());
        }
    }

    static class VisitService {
        private int count = 0;

        int visit() {
            return ++count;
        }

        int count() {
            return count;
        }
    }

    static class SafeVisitService {
        private final AtomicLong count = new AtomicLong();

        long visit() {
            return count.incrementAndGet();
        }
    }

    @Test
    @DisplayName("싱글톤 서비스의 int 필드를 여러 스레드가 올리면 부른 수보다 작을 수 있고, AtomicLong이면 정확하다")
    void serviceFieldIsShared() throws Exception {
        int lost = -1;
        for (int attempt = 0; attempt < 10 && lost < 0; attempt++) {
            VisitService service = new VisitService();
            runOn(8, 100_000, service::visit);
            if (service.count() < 800_000) {
                lost = service.count();
            }
        }
        assertThat(lost).as("10번 가운데 한 번은 갱신이 사라진다").isBetween(0, 799_999);

        SafeVisitService safe = new SafeVisitService();
        runOn(8, 100_000, safe::visit);
        assertThat(safe.count.get()).isEqualTo(800_000);
        CiOutput.record(LESSON, "service", "스레드 8개 × visit() 10만 번 = 80만 번\n"
                + "int 필드      → " + String.format("%,d", lost) + "\n"
                + "AtomicLong 필드 → " + String.format("%,d", safe.count.get()));
    }

    static void runOn(int threads, int times, Runnable call) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> list = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread th = new Thread(() -> {
                await(start);
                for (int i = 0; i < times; i++) {
                    call.run();
                }
            });
            th.start();
            list.add(th);
        }
        start.countDown();
        for (Thread th : list) {
            th.join();
        }
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
