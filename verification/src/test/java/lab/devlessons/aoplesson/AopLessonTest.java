package lab.devlessons.aoplesson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lab.devlessons.support.CiOutput;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.annotation.Order;

/**
 * 레슨 "AOP"(S9)의 주장을 작은 스프링 컨테이너로 확인한다. 감사 로그는 DB가 아니라 메모리 리스트에 남긴다.
 * 예전 레슨 "AOP와 감사 로그"의 DB를 쓰는 검증은 audit/AuditAspectTest에 그대로 있다.
 */
class AopLessonTest {

    private static final String LESSON = "aop-audit";
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
        String value();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Timed {
    }

    public static class LimitRepository {
        final int[] limits = {0, 100, 100};
    }

    /** 레슨의 코드 predict와 self */
    public static class LimitService {
        private final LimitRepository repository;

        public LimitService(LimitRepository repository) {
            this.repository = repository;
        }

        @Audited("CHANGE_LIMIT")
        public void changeLimit(int id, int limit) {
            if (limit < 0) {
                throw new IllegalArgumentException("한도는 0 이상");
            }
            repository.limits[id] = limit;
        }

        @Audited("CHANGE_LIMIT")
        public final String changeLimitFinal(int id, int limit) {
            if (repository == null) {
                return "repository=null (프록시 객체 위에서 돌았다)";
            }
            repository.limits[id] = limit;
            return "repository 있음";
        }

        public void resetLimit(int id) {
            this.changeLimit(id, 0);
        }

        public void resetViaPrivate(int id) {
            privateChange(id, 0);
        }

        @Audited("CHANGE_LIMIT")
        private void privateChange(int id, int limit) {
            repository.limits[id] = limit;
        }
    }

    /** 내부 호출을 고친 모양: 한도를 바꾸는 일을 다른 빈으로 */
    public static class LimitChanger {
        private final LimitRepository repository;

        public LimitChanger(LimitRepository repository) {
            this.repository = repository;
        }

        @Audited("CHANGE_LIMIT")
        public void change(int id, int limit) {
            repository.limits[id] = limit;
        }
    }

    public static class SplitLimitService {
        private final LimitChanger changer;

        public SplitLimitService(LimitChanger changer) {
            this.changer = changer;
        }

        public void resetLimit(int id) {
            changer.change(id, 0);
        }
    }

    /** 레슨의 코드 aspect */
    @Aspect
    @Order(1)
    public static class AuditAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint jp, Audited audited) throws Throwable {
            LOG.add("Audit 시작");
            try {
                Object result = jp.proceed();
                LOG.add("감사 로그: " + audited.value() + " SUCCESS");
                return result;
            } catch (Throwable e) {
                LOG.add("감사 로그: " + audited.value() + " FAILED (" + e.getMessage() + ")");
                throw e;
            } finally {
                LOG.add("Audit 끝");
            }
        }
    }

    @Aspect
    @Order(2)
    public static class TimingAspect {
        @Around("@annotation(lab.devlessons.aoplesson.AopLessonTest.Audited)")
        public Object time(ProceedingJoinPoint jp) throws Throwable {
            LOG.add("Timing 시작");
            try {
                return jp.proceed();
            } finally {
                LOG.add("Timing 끝");
            }
        }
    }

    /** 다섯 가지 어드바이스 */
    @Aspect
    public static class AllAdvice {
        @Before("@annotation(lab.devlessons.aoplesson.AopLessonTest.Timed)")
        public void before() {
            LOG.add("@Before");
        }

        @AfterReturning("@annotation(lab.devlessons.aoplesson.AopLessonTest.Timed)")
        public void afterReturning() {
            LOG.add("@AfterReturning");
        }

        @AfterThrowing("@annotation(lab.devlessons.aoplesson.AopLessonTest.Timed)")
        public void afterThrowing() {
            LOG.add("@AfterThrowing");
        }

        @After("@annotation(lab.devlessons.aoplesson.AopLessonTest.Timed)")
        public void after() {
            LOG.add("@After");
        }

        @Around("@annotation(lab.devlessons.aoplesson.AopLessonTest.Timed)")
        public Object around(ProceedingJoinPoint jp) throws Throwable {
            LOG.add("@Around 앞");
            try {
                return jp.proceed();
            } finally {
                LOG.add("@Around 뒤");
            }
        }
    }

    public static class ReportService {
        @Timed
        public String run(boolean fail) {
            LOG.add("메서드 실행");
            if (fail) {
                throw new IllegalStateException("실패");
            }
            return "ok";
        }
    }

    /** 예외를 삼키는 잘못된 애스펙트 */
    @Aspect
    public static class SwallowingAspect {
        @Around("@annotation(audited)")
        public Object audit(ProceedingJoinPoint jp, Audited audited) {
            try {
                return jp.proceed();
            } catch (Throwable e) {
                LOG.add("기록만 하고 삼킴: " + e.getMessage());
                return null;
            }
        }
    }

    public interface CouponService {
        void issue();
    }

    public static class CouponServiceImpl implements CouponService {
        @Audited("ISSUE_COUPON")
        public void issue() {
        }
    }

    public static final class FinalService {
        @Audited("FINAL")
        public void run() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkConfig {
    }

    private static AnnotationConfigApplicationContext ctx(Class<?>... classes) {
        return new AnnotationConfigApplicationContext(classes);
    }

    @Test
    @DisplayName("@Audited 메서드를 밖에서 부르면 감사 로그가 남고, 주입된 빈은 CGLIB 프록시다")
    void auditedAndProxy() {
        try (var c = ctx(CglibConfig.class, AuditAspect.class, LimitRepository.class, LimitService.class)) {
            LimitService service = c.getBean(LimitService.class);
            LOG.clear();
            service.changeLimit(1, 500);

            assertThat(LOG).contains("감사 로그: CHANGE_LIMIT SUCCESS");
            assertThat(AopUtils.isCglibProxy(service)).isTrue();
            assertThat(c.getBean(LimitRepository.class).limits[1]).isEqualTo(500);
            CiOutput.record(LESSON, "proxy", "주입된 LimitService의 클래스: " + service.getClass().getSimpleName()
                    + "\n원래 클래스: " + AopUtils.getTargetClass(service).getSimpleName()
                    + "\nchangeLimit(1, 500) 뒤 기록:\n" + String.join("\n", LOG));
            LOG.clear();
            assertThatThrownBy(() -> service.changeLimit(1, -1)).isInstanceOf(IllegalArgumentException.class);
            CiOutput.record(LESSON, "failure", "changeLimit(1, -1) 뒤 기록:\n" + String.join("\n", LOG)
                    + "\n호출한 쪽이 받은 예외: IllegalArgumentException");
        }
    }

    @Test
    @DisplayName("final 메서드와 private 메서드에는 어드바이스가 걸리지 않고, final 메서드는 프록시 객체 위에서 돌아 주입 필드가 null이다")
    void finalAndPrivateNotAdvised() {
        try (var c = ctx(CglibConfig.class, AuditAspect.class, LimitRepository.class, LimitService.class)) {
            LimitService service = c.getBean(LimitService.class);
            LOG.clear();
            service.changeLimit(1, 300);
            String finalResult = service.changeLimitFinal(2, 300);
            service.resetViaPrivate(2);
            long audits = LOG.stream().filter(l -> l.startsWith("감사 로그")).count();

            assertThat(audits).isEqualTo(1);
            assertThat(finalResult).contains("repository=null");
            CiOutput.record(LESSON, "final", "changeLimit (public) → 기록됨\nchangeLimitFinal (public final) → 기록 안 됨, "
                    + finalResult + "\nprivateChange (private, 같은 클래스에서 부름) → 기록 안 됨\n감사 로그 수: " + audits);
        }
    }

    @Test
    @DisplayName("final 클래스에 애스펙트가 걸리면 컨텍스트가 뜨지 않는다")
    void finalClassCannotBeProxied() {
        assertThatThrownBy(() -> ctx(CglibConfig.class, AuditAspect.class, FinalService.class))
                .satisfies(e -> {
                    Throwable t = e;
                    StringBuilder chain = new StringBuilder();
                    while (t != null) {
                        chain.append(t.getMessage()).append('\n');
                        t = t.getCause();
                    }
                    assertThat(chain.toString()).containsIgnoringCase("final");
                    String line = chain.toString().lines().filter(l -> l.toLowerCase().contains("final class"))
                            .findFirst().orElse(chain.toString().lines().findFirst().orElse(""));
                    CiOutput.record(LESSON, "final-class", line.replace("lab.devlessons.aoplesson.AopLessonTest$", ""));
                });
    }

    @Test
    @DisplayName("proxyTargetClass=false면 인터페이스가 있는 빈은 JDK 동적 프록시가 되고 구현 클래스 타입으로 꺼낼 수 없다")
    void jdkProxy() {
        try (var c = ctx(JdkConfig.class, AuditAspect.class, CouponServiceImpl.class)) {
            CouponService service = c.getBean(CouponService.class);

            assertThat(AopUtils.isJdkDynamicProxy(service)).isTrue();
            assertThatThrownBy(() -> c.getBean(CouponServiceImpl.class)).isInstanceOf(NoSuchBeanDefinitionException.class);
            CiOutput.record(LESSON, "jdk", "proxyTargetClass=false\ngetBean(CouponService) → " + service.getClass().getSimpleName()
                    + " (JDK 동적 프록시)\ngetBean(CouponServiceImpl) → NoSuchBeanDefinitionException");
        }
    }

    @Test
    @DisplayName("같은 클래스 안의 호출에는 어드바이스가 걸리지 않고, 다른 빈으로 나누면 걸린다")
    void selfInvocation() {
        try (var c = ctx(CglibConfig.class, AuditAspect.class, LimitRepository.class, LimitService.class,
                LimitChanger.class, SplitLimitService.class)) {
            LOG.clear();
            c.getBean(LimitService.class).resetLimit(1);
            List<String> self = new ArrayList<>(LOG);
            LOG.clear();
            c.getBean(SplitLimitService.class).resetLimit(2);
            List<String> split = new ArrayList<>(LOG);

            assertThat(c.getBean(LimitRepository.class).limits[1]).isZero();
            assertThat(self).noneMatch(l -> l.startsWith("감사 로그"));
            assertThat(split).contains("감사 로그: CHANGE_LIMIT SUCCESS");
            CiOutput.record(LESSON, "self", "LimitService.resetLimit → this.changeLimit\n  한도: 0으로 바뀜\n  감사 로그: "
                    + (self.isEmpty() ? "없음" : String.join(", ", self))
                    + "\n\nSplitLimitService.resetLimit → LimitChanger.change (다른 빈)\n  감사 로그: "
                    + split.stream().filter(l -> l.startsWith("감사 로그")).findFirst().orElse("없음"));
        }
    }

    @Test
    @DisplayName("한 애스펙트의 다섯 어드바이스 순서: 정상과 예외")
    void adviceKinds() {
        try (var c = ctx(CglibConfig.class, AllAdvice.class, ReportService.class)) {
            ReportService service = c.getBean(ReportService.class);
            LOG.clear();
            service.run(false);
            List<String> ok = new ArrayList<>(LOG);
            LOG.clear();
            assertThatThrownBy(() -> service.run(true)).isInstanceOf(IllegalStateException.class);
            List<String> fail = new ArrayList<>(LOG);

            assertThat(ok).containsExactly("@Around 앞", "@Before", "메서드 실행", "@AfterReturning", "@After", "@Around 뒤");
            assertThat(fail).contains("@AfterThrowing").doesNotContain("@AfterReturning");
            CiOutput.record(LESSON, "kinds", "정상:\n" + String.join("\n", ok) + "\n\n예외:\n" + String.join("\n", fail));
        }
    }

    @Test
    @DisplayName("@Order(1) 애스펙트가 @Order(2)를 감싼다")
    void aspectOrder() {
        try (var c = ctx(CglibConfig.class, TimingAspect.class, AuditAspect.class, LimitRepository.class, LimitService.class)) {
            LOG.clear();
            c.getBean(LimitService.class).changeLimit(1, 200);

            assertThat(LOG).containsExactly("Audit 시작", "Timing 시작", "Timing 끝", "감사 로그: CHANGE_LIMIT SUCCESS", "Audit 끝");
            CiOutput.record(LESSON, "order", "@Order(1) AuditAspect, @Order(2) TimingAspect\n" + String.join("\n", LOG));
        }
    }

    @Test
    @DisplayName("애스펙트가 예외를 삼키면 호출한 쪽은 예외를 받지 않는다")
    void swallowingAspect() {
        try (var c = ctx(CglibConfig.class, SwallowingAspect.class, LimitRepository.class, LimitService.class)) {
            LOG.clear();
            c.getBean(LimitService.class).changeLimit(1, -1);

            assertThat(LOG).containsExactly("기록만 하고 삼킴: 한도는 0 이상");
            CiOutput.record(LESSON, "swallow", "changeLimit(1, -1)\n" + String.join("\n", LOG) + "\n호출한 쪽: 예외 없음 (성공한 줄 안다)");
        }
    }
}
