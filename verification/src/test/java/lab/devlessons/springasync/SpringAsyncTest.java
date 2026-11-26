package lab.devlessons.springasync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 레슨 "@Async"(S14)의 주장을 작은 스프링 부트 앱으로 확인한다. 실행기는 스프링 부트의 자동 설정
 * (TaskExecutionAutoConfiguration)을 그대로 쓴다. 트랜잭션은 DB 없이 ResourcelessTransactionManager로 연다.
 */
class SpringAsyncTest {

    private static final String LESSON = "spring-async";
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());
    static final List<Throwable> HANDLED = Collections.synchronizedList(new ArrayList<>());

    public static class ReceiptMailer {
        // 주입된 것은 프록시라 필드에 넣으면 진짜 객체에 닿지 않는다. 테스트용 걸쇠는 static으로 둔다
        static volatile CountDownLatch done;
        static volatile CountDownLatch release;

        /** 레슨의 코드 predict */
        @Async
        public void send(String receipt) throws InterruptedException {
            LOG.add("send 스레드: " + Thread.currentThread().getName());
            Thread.sleep(300);
            if (done != null) {
                done.countDown();
            }
        }

        @Async
        public void block() throws InterruptedException {
            release.await(10, TimeUnit.SECONDS);
        }

        @Async
        public void failVoid() {
            throw new IllegalStateException("메일 서버 응답 없음");
        }

        @Async
        public CompletableFuture<Void> failFuture() {
            throw new IllegalStateException("메일 서버 응답 없음");
        }

        public void sendAllInside() throws InterruptedException {
            LOG.add("sendAllInside 스레드: " + Thread.currentThread().getName());
            this.send("r1");
        }

        @Async
        @Transactional
        public CompletableFuture<String> inside() {
            return CompletableFuture.completedFuture("스레드 " + Thread.currentThread().getName()
                    + ", 트랜잭션 " + (TransactionSynchronizationManager.isActualTransactionActive() ? "있음(새 트랜잭션)" : "없음")
                    + ", 로그인 사용자 " + SecurityContextHolder.getContext().getAuthentication());
        }
    }

    public static class Caller {
        private final ReceiptMailer mailer;

        public Caller(ReceiptMailer mailer) {
            this.mailer = mailer;
        }

        @Transactional
        public String callFromTransaction() throws Exception {
            String here = "호출한 쪽: 트랜잭션 " + (TransactionSynchronizationManager.isActualTransactionActive() ? "있음" : "없음")
                    + ", 로그인 사용자 " + SecurityContextHolder.getContext().getAuthentication().getName();
            return here + "\n@Async 메서드: " + mailer.inside().get(5, TimeUnit.SECONDS);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    @ImportAutoConfiguration(TaskExecutionAutoConfiguration.class)
    static class App implements AsyncConfigurer {
        @Bean
        ReceiptMailer receiptMailer() {
            return new ReceiptMailer();
        }

        @Bean
        Caller caller(ReceiptMailer mailer) {
            return new Caller(mailer);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new ResourcelessTransactionManager();
        }

        /** 레슨의 코드 handler */
        @Override
        public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
            return (Throwable e, Method m, Object... params) -> HANDLED.add(e);
        }
    }

    private static ConfigurableApplicationContext run(String... props) {
        String[] all = new String[props.length + 2];
        all[0] = "spring.main.banner-mode=off";
        all[1] = "spring.main.log-startup-info=false";
        System.arraycopy(props, 0, all, 2, props.length);
        return new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).properties(all).run();
    }

    @Test
    @DisplayName("@Async 메서드는 task-N 스레드에서 돌고, 부른 쪽은 끝나길 기다리지 않는다. 내부 호출은 같은 스레드다")
    void runsOnTaskThread() throws Exception {
        try (var ctx = run()) {
            ReceiptMailer mailer = ctx.getBean(ReceiptMailer.class);
            LOG.clear();
            ReceiptMailer.done = new CountDownLatch(1);
            long start = System.nanoTime();
            mailer.send("r1");
            long returnedMs = (System.nanoTime() - start) / 1_000_000;
            ReceiptMailer.done.await(5, TimeUnit.SECONDS);
            String thread = LOG.get(0);

            assertThat(thread).matches("send 스레드: task-\\d+");
            assertThat(returnedMs).isLessThan(200);

            LOG.clear();
            ReceiptMailer.done = null;
            mailer.sendAllInside();
            List<String> inside = new ArrayList<>(LOG);
            assertThat(inside.get(1)).isEqualTo(inside.get(0).replace("sendAllInside", "send"));
            CiOutput.record(LESSON, "thread", "부른 쪽 스레드: " + Thread.currentThread().getName().replace("Test worker", "main")
                    + "\n" + thread + "\nsend는 300ms 걸리지만 부른 쪽은 " + returnedMs + "ms 만에 돌아옴"
                    + "\n\n같은 클래스 안에서 this.send(...)로 부름\n" + String.join("\n", inside).replace("Test worker", "main"));
        }
    }

    @Test
    @DisplayName("자동 설정 실행기는 코어 8, 큐 무제한이라 작업 1,000개를 넣어도 스레드는 8개다")
    void defaultExecutor() throws Exception {
        try (var ctx = run()) {
            ThreadPoolTaskExecutor exec = ctx.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            ReceiptMailer mailer = ctx.getBean(ReceiptMailer.class);
            ReceiptMailer.release = new CountDownLatch(1);
            for (int i = 0; i < 1000; i++) {
                mailer.block();
            }
            Thread.sleep(300);
            int pool = exec.getPoolSize();
            int queued = exec.getQueueSize();
            ReceiptMailer.release.countDown();

            assertThat(exec.getCorePoolSize()).isEqualTo(8);
            assertThat(exec.getMaxPoolSize()).isEqualTo(Integer.MAX_VALUE);
            assertThat(exec.getQueueCapacity()).isEqualTo(Integer.MAX_VALUE);
            assertThat(pool).isEqualTo(8);
            CiOutput.record(LESSON, "defaults", "applicationTaskExecutor\n코어 스레드: " + exec.getCorePoolSize()
                    + "\n최대 스레드: " + exec.getMaxPoolSize() + "\n큐 크기: " + exec.getQueueCapacity()
                    + "\n\n멈춰 있는 작업 1,000개를 넣은 뒤\n스레드: " + pool + "개\n큐에서 기다리는 작업: " + queued + "개");
        }
    }

    @Test
    @DisplayName("코어 2, 최대 4, 큐 10이면 작업 20개 가운데 14개를 받고 나머지는 부른 쪽에서 TaskRejectedException")
    void boundedExecutorRejects() throws Exception {
        try (var ctx = run("spring.task.execution.pool.core-size=2", "spring.task.execution.pool.max-size=4",
                "spring.task.execution.pool.queue-capacity=10")) {
            ReceiptMailer mailer = ctx.getBean(ReceiptMailer.class);
            ThreadPoolTaskExecutor exec = ctx.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
            ReceiptMailer.release = new CountDownLatch(1);
            int accepted = 0;
            int rejected = 0;
            for (int i = 0; i < 20; i++) {
                try {
                    mailer.block();
                    accepted++;
                } catch (TaskRejectedException e) {
                    rejected++;
                }
            }
            int pool = exec.getPoolSize();
            ReceiptMailer.release.countDown();

            assertThat(accepted).isEqualTo(14);
            assertThat(rejected).isEqualTo(6);
            CiOutput.record(LESSON, "bounded", "core-size=2, max-size=4, queue-capacity=10\n멈춰 있는 작업 20개를 넣음\n받음: "
                    + accepted + "개 (스레드 " + pool + "개 + 큐 10개)\n거절: " + rejected + "개 (부른 쪽에서 TaskRejectedException)");
        }
    }

    @Test
    @DisplayName("void @Async의 예외는 부른 쪽에 오지 않고 AsyncUncaughtExceptionHandler로 가고, CompletableFuture면 join에서 받는다")
    void exceptions() throws Exception {
        try (var ctx = run()) {
            ReceiptMailer mailer = ctx.getBean(ReceiptMailer.class);
            HANDLED.clear();
            mailer.failVoid();
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (HANDLED.isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            AtomicReference<Throwable> joined = new AtomicReference<>();
            CompletableFuture<Void> f = mailer.failFuture();
            try {
                f.join();
            } catch (CompletionException e) {
                joined.set(e);
            }

            assertThat(HANDLED).hasSize(1);
            assertThat(joined.get()).isInstanceOf(CompletionException.class).hasCauseInstanceOf(IllegalStateException.class);
            CiOutput.record(LESSON, "exceptions", "void failVoid() 호출 → 부른 쪽: 예외 없음\n  핸들러가 받음: "
                    + HANDLED.get(0).getClass().getSimpleName() + ": " + HANDLED.get(0).getMessage()
                    + "\nCompletableFuture failFuture() 호출 → join(): " + joined.get().getClass().getSimpleName()
                    + " (원인 " + joined.get().getCause().getClass().getSimpleName() + ")");
        }
    }

    @Test
    @DisplayName("@Async 메서드에는 부른 쪽의 트랜잭션과 SecurityContextHolder가 따라가지 않는다")
    void contextDoesNotFollow() throws Exception {
        try (var ctx = run()) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("kim", null, List.of()));
            try {
                String out = ctx.getBean(Caller.class).callFromTransaction();

                assertThat(out).contains("호출한 쪽: 트랜잭션 있음, 로그인 사용자 kim");
                assertThat(out).contains("트랜잭션 있음(새 트랜잭션)").contains("로그인 사용자 null");
                CiOutput.record(LESSON, "context", out);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
