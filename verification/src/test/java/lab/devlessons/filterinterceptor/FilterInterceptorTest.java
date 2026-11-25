package lab.devlessons.filterinterceptor;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 레슨 "필터와 인터셉터"(S7)의 주장을 작은 웹 앱(MiniWeb)으로 확인한다. 경우마다 앱을 따로 띄운다. */
class FilterInterceptorTest {

    private static final String LESSON = "filter-interceptor";
    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface LoginRequired {
    }

    public static class NotFoundOrder extends RuntimeException {
    }

    @RestController
    static class OrderController {
        @GetMapping("/orders/1")
        String order() {
            LOG.add("컨트롤러");
            return "order 1";
        }

        @GetMapping("/orders/public")
        String publicOrders() {
            LOG.add("컨트롤러(public)");
            return "public";
        }

        @LoginRequired
        @GetMapping("/orders/mine")
        String mine() {
            LOG.add("컨트롤러(mine)");
            return "my orders";
        }

        @GetMapping("/orders/boom")
        String boom() {
            LOG.add("컨트롤러(예외)");
            throw new IllegalStateException("주문 처리 실패");
        }

        @GetMapping("/orders/missing")
        String missing() {
            LOG.add("컨트롤러(없는 주문)");
            throw new NotFoundOrder();
        }

        @ExceptionHandler(NotFoundOrder.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        String notFound() {
            return "없는 주문";
        }
    }

    /** 이름을 받아 앞뒤를 기록하는 필터. 예외가 올라오면 그것도 기록한다 */
    static Filter loggingFilter(String name) {
        return (req, res, chain) -> {
            LOG.add(name + " 앞");
            try {
                chain.doFilter(req, res);
            } catch (IOException | ServletException | RuntimeException e) {
                LOG.add(name + "가 예외를 봄: " + e.getClass().getSimpleName());
                throw e;
            } finally {
                LOG.add(name + " 뒤");
            }
        };
    }

    static HandlerInterceptor loggingInterceptor(String name) {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                LOG.add(name + " preHandle");
                return true;
            }

            @Override
            public void postHandle(HttpServletRequest req, HttpServletResponse res, Object handler, ModelAndView mv) {
                LOG.add(name + " postHandle");
            }

            @Override
            public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
                LOG.add(name + " afterCompletion" + (ex == null ? "" : " (예외: " + ex.getClass().getSimpleName() + ")"));
            }
        };
    }

    /** 레슨의 코드 annotation */
    static class LoginInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
            if (handler instanceof HandlerMethod m && m.hasMethodAnnotation(LoginRequired.class)
                    && req.getHeader("X-User") == null) {
                LOG.add("로그인 검사: 막음");
                res.setStatus(401);
                return false;
            }
            return true;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OneOfEach implements WebMvcConfigurer {
        @Bean
        FilterRegistrationBean<Filter> filter() {
            FilterRegistrationBean<Filter> b = new FilterRegistrationBean<>(loggingFilter("필터"));
            b.addUrlPatterns("/*");
            return b;
        }

        @Override
        public void addInterceptors(InterceptorRegistry r) {
            r.addInterceptor(loggingInterceptor("인터셉터")).addPathPatterns("/orders/**");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoOfEach implements WebMvcConfigurer {
        @Bean
        FilterRegistrationBean<Filter> filterA() {
            FilterRegistrationBean<Filter> b = new FilterRegistrationBean<>(loggingFilter("필터A"));
            b.addUrlPatterns("/*");
            b.setOrder(1);
            return b;
        }

        @Bean
        FilterRegistrationBean<Filter> filterB() {
            FilterRegistrationBean<Filter> b = new FilterRegistrationBean<>(loggingFilter("필터B"));
            b.addUrlPatterns("/*");
            b.setOrder(2);
            return b;
        }

        @Override
        public void addInterceptors(InterceptorRegistry r) {
            r.addInterceptor(loggingInterceptor("X")).addPathPatterns("/orders/**");
            r.addInterceptor(loggingInterceptor("Y")).addPathPatterns("/orders/**");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LoginConfig implements WebMvcConfigurer {
        @Bean
        FilterRegistrationBean<Filter> filter() {
            FilterRegistrationBean<Filter> b = new FilterRegistrationBean<>(loggingFilter("필터"));
            b.addUrlPatterns("/*");
            return b;
        }

        @Override
        public void addInterceptors(InterceptorRegistry r) {
            r.addInterceptor(new LoginInterceptor()).addPathPatterns("/orders/**").excludePathPatterns("/orders/public");
            r.addInterceptor(loggingInterceptor("X")).addPathPatterns("/orders/**").excludePathPatterns("/orders/public");
        }
    }

    /** @Component만 붙이고 addInterceptors에 등록하지 않은 인터셉터 */
    @Component
    static class ForgottenInterceptor implements HandlerInterceptor {
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
            CALLS.incrementAndGet();
            return true;
        }
    }

    /** 일반 Filter와 OncePerRequestFilter를 에러 디스패치까지 걸어 둔다 */
    @Configuration(proxyBeanMethods = false)
    static class ErrorDispatchConfig {
        static final AtomicInteger PLAIN = new AtomicInteger();
        static final AtomicInteger ONCE = new AtomicInteger();

        @Bean
        FilterRegistrationBean<Filter> plain() {
            FilterRegistrationBean<Filter> b = new FilterRegistrationBean<>((req, res, chain) -> {
                PLAIN.incrementAndGet();
                chain.doFilter(req, res);
            });
            b.addUrlPatterns("/*");
            b.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
            return b;
        }

        @Bean
        FilterRegistrationBean<OncePerRequestFilter> once() {
            FilterRegistrationBean<OncePerRequestFilter> b = new FilterRegistrationBean<>(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    ONCE.incrementAndGet();
                    chain.doFilter(req, res);
                }
            });
            b.addUrlPatterns("/*");
            b.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
            return b;
        }
    }

    private static List<String> call(MiniWeb web, String path, String user) {
        LOG.clear();
        var b = web.request(path).GET();
        if (user != null) {
            b.header("X-User", user);
        }
        int status = web.exchange(b).statusCode();
        // 응답 바이트는 afterCompletion과 필터 뒤보다 먼저 클라이언트에 닿을 수 있다.
        // 서버 스레드가 가장 바깥 필터의 뒤를 기록할 때까지 기다린 뒤 읽는다(CI에서 가끔 끝이 잘렸다)
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (!LOG.contains("필터 뒤") && !LOG.contains("필터A 뒤") && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        List<String> copy = new ArrayList<>(LOG);
        copy.add("→ 응답 " + status);
        return copy;
    }

    @Test
    @DisplayName("정상 요청: 필터 앞, preHandle, 컨트롤러, postHandle, afterCompletion, 필터 뒤")
    void normalOrder() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, OneOfEach.class)) {
            List<String> log = call(web, "/orders/1", null);

            assertThat(log).containsExactly("필터 앞", "인터셉터 preHandle", "컨트롤러", "인터셉터 postHandle",
                    "인터셉터 afterCompletion", "필터 뒤", "→ 응답 200");
            CiOutput.record(LESSON, "normal", "GET /orders/1\n" + String.join("\n", log));
        }
    }

    @Test
    @DisplayName("없는 경로에는 필터만 불리고 인터셉터는 불리지 않는다")
    void notFoundOnlyFilter() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, OneOfEach.class)) {
            List<String> log = call(web, "/no-such-path", null);

            assertThat(log).doesNotContain("인터셉터 preHandle");
            assertThat(log).contains("필터 앞", "필터 뒤", "→ 응답 404");
            CiOutput.record(LESSON, "not-found", "GET /no-such-path\n" + String.join("\n", log));
        }
    }

    @Test
    @DisplayName("필터 둘과 인터셉터 둘: 앞쪽은 정한 순서, 뒤쪽은 반대 순서")
    void twoOfEachOrder() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, TwoOfEach.class)) {
            List<String> log = call(web, "/orders/1", null);

            assertThat(log).containsExactly("필터A 앞", "필터B 앞", "X preHandle", "Y preHandle", "컨트롤러",
                    "Y postHandle", "X postHandle", "Y afterCompletion", "X afterCompletion", "필터B 뒤", "필터A 뒤", "→ 응답 200");
            CiOutput.record(LESSON, "two-of-each", String.join("\n", log));
        }
    }

    @Test
    @DisplayName("@Component만 붙이고 등록하지 않은 인터셉터는 불리지 않는다")
    void forgottenInterceptor() {
        ForgottenInterceptor.CALLS.set(0);
        try (MiniWeb web = MiniWeb.start(OrderController.class, ForgottenInterceptor.class)) {
            // 필터가 없는 앱이라 call의 기다림을 쓰지 않는다
            web.exchange(web.request("/orders/1").GET());

            assertThat(web.context().getBeansOfType(ForgottenInterceptor.class)).hasSize(1);
            assertThat(ForgottenInterceptor.CALLS.get()).isZero();
            CiOutput.record(LESSON, "forgotten", "ForgottenInterceptor 빈: " + web.context().getBeansOfType(ForgottenInterceptor.class).size()
                    + "개\nGET /orders/1 뒤 preHandle 호출: " + ForgottenInterceptor.CALLS.get() + "번");
        }
    }

    @Test
    @DisplayName("@LoginRequired 메서드에 X-User가 없으면 preHandle이 false: 401, 컨트롤러와 postHandle은 없고 필터 뒤는 있다. exclude 경로에는 안 걸린다")
    void loginInterceptor() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, LoginConfig.class)) {
            List<String> blocked = call(web, "/orders/mine", null);
            List<String> allowed = call(web, "/orders/mine", "kim");
            List<String> plain = call(web, "/orders/1", null);
            List<String> excluded = call(web, "/orders/public", null);

            assertThat(blocked).containsExactly("필터 앞", "로그인 검사: 막음", "필터 뒤", "→ 응답 401");
            assertThat(allowed).contains("컨트롤러(mine)", "→ 응답 200");
            assertThat(plain).contains("컨트롤러", "→ 응답 200");
            assertThat(excluded).doesNotContain("X preHandle");
            CiOutput.record(LESSON, "login", "GET /orders/mine (X-User 없음)\n" + String.join("\n", blocked)
                    + "\n\nGET /orders/mine (X-User: kim)\n" + String.join("\n", allowed)
                    + "\n\nGET /orders/1 (@LoginRequired 없음, X-User 없음)\n" + String.join("\n", plain));
            CiOutput.record(LESSON, "exclude", "GET /orders/public (excludePathPatterns)\n" + String.join("\n", excluded));
        }
    }

    @Test
    @DisplayName("컨트롤러 예외: postHandle은 없고 afterCompletion은 예외를 받는다. 처리하지 않은 예외는 필터까지 올라오고, @ExceptionHandler가 처리한 예외는 올라오지 않는다")
    void exceptionPath() {
        try (MiniWeb web = MiniWeb.start(OrderController.class, OneOfEach.class)) {
            List<String> unhandled = call(web, "/orders/boom", null);
            List<String> handled = call(web, "/orders/missing", null);

            assertThat(unhandled).doesNotContain("인터셉터 postHandle");
            assertThat(unhandled).contains("인터셉터 afterCompletion (예외: IllegalStateException)");
            assertThat(unhandled).anyMatch(l -> l.startsWith("필터가 예외를 봄"));
            assertThat(handled).noneMatch(l -> l.startsWith("필터가 예외를 봄"));
            assertThat(handled).contains("→ 응답 404");
            CiOutput.record(LESSON, "exception", "GET /orders/boom (처리하지 않은 예외)\n" + String.join("\n", unhandled)
                    + "\n\nGET /orders/missing (@ExceptionHandler가 404로 바꿈)\n" + String.join("\n", handled));
        }
    }

    @Test
    @DisplayName("처리하지 않은 예외로 에러 페이지에 다시 보내질 때 일반 필터는 두 번, OncePerRequestFilter는 한 번 불린다")
    void oncePerRequest() {
        ErrorDispatchConfig.PLAIN.set(0);
        ErrorDispatchConfig.ONCE.set(0);
        try (MiniWeb web = MiniWeb.start(OrderController.class, ErrorDispatchConfig.class)) {
            int status = web.exchange(web.request("/orders/boom").GET()).statusCode();

            assertThat(status).isEqualTo(500);
            assertThat(ErrorDispatchConfig.PLAIN.get()).isEqualTo(2);
            assertThat(ErrorDispatchConfig.ONCE.get()).isEqualTo(1);
            CiOutput.record(LESSON, "once", "GET /orders/boom → " + status + " (톰캣이 /error로 다시 보냄)\n일반 Filter 호출: "
                    + ErrorDispatchConfig.PLAIN.get() + "번\nOncePerRequestFilter 호출: " + ErrorDispatchConfig.ONCE.get() + "번");
        }
    }
}
