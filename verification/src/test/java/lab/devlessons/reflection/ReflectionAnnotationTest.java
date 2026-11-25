package lab.devlessons.reflection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.MiniWeb;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 레슨 "리플렉션과 어노테이션"(J17)의 주장을 검증한다.
 * OrderService와 OrderRepository는 이 파일 아래의 최상위 클래스다. 테스트 클래스의 중첩 클래스로 두면
 * 같은 nest라서 private에 setAccessible 없이도 닿기 때문이다.
 */
class ReflectionAnnotationTest {

    private static final String LESSON = "reflection-annotation";

    @Test
    @DisplayName("세 방법으로 얻은 Class 객체는 같고, getDeclaredFields는 private을 포함하고 getFields는 public만 준다")
    void classObject() throws Exception {
        OrderService service = new OrderService();
        Class<?> a = OrderService.class;
        Class<?> b = service.getClass();
        Class<?> c = Class.forName("lab.devlessons.reflection.OrderService");
        Object o = "주문";

        assertThat(a).isSameAs(b).isSameAs(c);
        assertThat(a.getDeclaredFields()).hasSize(1);
        assertThat(a.getFields()).isEmpty();
        assertThat(o.getClass() == String.class).isTrue();

        StringBuilder fields = new StringBuilder();
        for (Field f : a.getDeclaredFields()) {
            fields.append(f.getType().getSimpleName()).append(' ').append(f.getName());
        }
        CiOutput.record(LESSON, "inspect", fields.toString());
        CiOutput.record(LESSON, "class-object", """
                OrderService.class == service.getClass()      → %s
                service.getClass() == Class.forName("...")    → %s
                getDeclaredFields().length → %d
                getFields().length         → %d

                Object o = "주문";
                o.getClass() == String.class → %s""".formatted(a == b, b == c, a.getDeclaredFields().length,
                a.getFields().length, o.getClass() == String.class));
    }

    @Test
    @DisplayName("setAccessible(true) 뒤에는 다른 클래스의 private 필드에 값을 넣을 수 있고, 부르지 않으면 IllegalAccessException이다")
    void setPrivateField() throws Exception {
        OrderService service = new OrderService();
        Field f = OrderService.class.getDeclaredField("repo");

        Exception without = null;
        try {
            f.set(service, new OrderRepository());
        } catch (IllegalAccessException e) {
            without = e;
        }
        f.setAccessible(true);
        f.set(service, new OrderRepository());

        assertThat(without).isInstanceOf(IllegalAccessException.class);
        assertThat(service.order()).isEqualTo("주문 저장됨");
        CiOutput.record(LESSON, "set-field", """
                setAccessible 없이 f.set(...)
                → %s

                setAccessible(true) 뒤 f.set(...)
                → service.order() = %s""".formatted(without.getClass().getSimpleName(), service.order()));
    }

    @Test
    @DisplayName("없는 필드 이름은 컴파일되고 실행 중에 NoSuchFieldException을 던진다")
    void typoFailsAtRuntime() {
        Exception error = null;
        try {
            OrderService.class.getDeclaredField("repoo");
        } catch (NoSuchFieldException e) {
            error = e;
        }
        assertThat(error).isInstanceOf(NoSuchFieldException.class);
        CiOutput.record(LESSON, "typo", "OrderService.class.getDeclaredField(\"repoo\")\n→ 컴파일됨, 실행하면 "
                + error.getClass().getName() + ": " + error.getMessage());
    }

    // 어노테이션 셋: 남는 기간만 다르다
    @Target(ElementType.METHOD)
    @interface LoginRequiredDefault {
    }

    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    @interface LoginRequiredSource {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface LoginRequired {
        String role() default "USER";
    }

    static class OrderController {
        @LoginRequiredDefault
        public String myOrders() {
            return "...";
        }

        @LoginRequiredSource
        public String draft() {
            return "...";
        }

        @LoginRequired(role = "ADMIN")
        public String allOrders() {
            return "...";
        }
    }

    @Test
    @DisplayName("@Retention이 없거나 SOURCE인 어노테이션은 실행 중에 보이지 않고, RUNTIME이면 보이고 요소의 값도 읽힌다")
    void retention() throws Exception {
        Method myOrders = OrderController.class.getMethod("myOrders");
        Method draft = OrderController.class.getMethod("draft");
        Method all = OrderController.class.getMethod("allOrders");

        boolean byDefault = myOrders.isAnnotationPresent(LoginRequiredDefault.class);
        boolean bySource = draft.isAnnotationPresent(LoginRequiredSource.class);
        boolean byRuntime = all.isAnnotationPresent(LoginRequired.class);
        String role = all.getAnnotation(LoginRequired.class).role();

        assertThat(byDefault).isFalse();
        assertThat(bySource).isFalse();
        assertThat(byRuntime).isTrue();
        assertThat(role).isEqualTo("ADMIN");
        CiOutput.record(LESSON, "predict", "m.isAnnotationPresent(LoginRequired.class)\n→ " + byDefault
                + "  (@Retention을 적지 않음)");
        CiOutput.record(LESSON, "retention", """
                @Retention 없음 (기본값 CLASS) → %s
                @Retention(SOURCE)             → %s
                @Retention(RUNTIME)            → %s, role() = %s""".formatted(byDefault, bySource, byRuntime, role));
    }

    // 코드 container
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Inject {
    }

    static Object create(Class<?> type) throws Exception {
        Object bean = type.getDeclaredConstructor().newInstance();

        for (Field f : type.getDeclaredFields()) {
            if (f.isAnnotationPresent(Inject.class)) {
                f.setAccessible(true);
                f.set(bean, create(f.getType()));
            }
        }
        return bean;
    }

    @Test
    @DisplayName("작은 컨테이너는 @Inject가 붙은 private 필드에 객체를 만들어 넣고, 표시가 없으면 건너뛴다")
    void tinyContainer() throws Exception {
        InjectedOrderService withInject = (InjectedOrderService) create(InjectedOrderService.class);
        OrderService without = (OrderService) create(OrderService.class);

        assertThat(withInject.order()).isEqualTo("주문 저장됨");
        assertThat(without.repo()).isNull();
        CiOutput.record(LESSON, "container", """
                create(OrderService.class)  (repo에 @Inject)
                → repo = %s
                → service.order() = %s

                create(OrderService.class)  (@Inject를 지움)
                → repo = %s""".formatted(withInject.repo().getClass().getSimpleName() + " 객체",
                withInject.order(), without.repo()));
    }

    @Service
    static class PayService {
    }

    @Test
    @DisplayName("@Service만 붙은 클래스에 자바의 isAnnotationPresent(Component)는 false이고 스프링의 AnnotatedElementUtils는 true다")
    void metaAnnotation() {
        boolean java = PayService.class.isAnnotationPresent(Component.class);
        boolean spring = AnnotatedElementUtils.hasAnnotation(PayService.class, Component.class);
        boolean serviceHasComponent = Service.class.isAnnotationPresent(Component.class);

        assertThat(java).isFalse();
        assertThat(spring).isTrue();
        assertThat(serviceHasComponent).isTrue();
        CiOutput.record(LESSON, "meta", """
                Service.class.isAnnotationPresent(Component.class)    → %s
                PayService.class.isAnnotationPresent(Component.class) → %s
                AnnotatedElementUtils.hasAnnotation(
                    PayService.class, Component.class)                → %s""".formatted(serviceHasComponent, java,
                spring));
    }

    // 실무 구역: 인터셉터가 읽는 어노테이션
    @RestController
    static class MyOrderController {
        @LoginRequired
        @GetMapping("/orders/runtime")
        String runtime() {
            return "내 주문 목록";
        }

        @LoginRequiredDefault
        @GetMapping("/orders/default")
        String byDefault() {
            return "내 주문 목록";
        }
    }

    static class LoginInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            if (handler instanceof HandlerMethod method) {
                boolean required = method.getMethodAnnotation(LoginRequired.class) != null
                        || method.getMethodAnnotation(LoginRequiredDefault.class) != null;
                var session = request.getSession(false);
                if (required && (session == null || session.getAttribute("user") == null)) {
                    response.setStatus(401);
                    return false;
                }
            }
            return true;
        }
    }

    @Configuration
    static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(new LoginInterceptor());
        }
    }

    @Test
    @DisplayName("RUNTIME인 @LoginRequired가 붙은 메서드로 온 로그인하지 않은 요청은 401이고, @Retention이 없으면 200으로 통과한다")
    void interceptorReadsAnnotation() {
        try (MiniWeb web = MiniWeb.start(MyOrderController.class, WebConfig.class)) {
            MiniWeb.Reply runtime = web.get("/orders/runtime");
            MiniWeb.Reply byDefault = web.get("/orders/default");

            assertThat(runtime.status()).isEqualTo(401);
            assertThat(byDefault.status()).isEqualTo(200);
            CiOutput.record(LESSON, "interceptor", """
                    로그인하지 않고 요청
                    GET /orders/mine  (@Retention 없음)   → %d %s
                    GET /orders/mine  (@Retention(RUNTIME)) → %d""".formatted(byDefault.status(), byDefault.body(),
                    runtime.status()));
        }
    }

    record OrderDto(long id) {
    }

    @Test
    @DisplayName("TypeReference의 익명 자식 클래스에서 getGenericSuperclass로 List<OrderDto>를 읽는다")
    void typeReference() {
        TypeReference<List<OrderDto>> ref = new TypeReference<>() {
        };
        Type parent = ref.getClass().getGenericSuperclass();
        Type arg = ((ParameterizedType) parent).getActualTypeArguments()[0];
        List<OrderDto> plain = new java.util.ArrayList<>();

        assertThat(arg.getTypeName()).isEqualTo("java.util.List<" + OrderDto.class.getName() + ">");
        assertThat(ref.getType()).isEqualTo(arg);
        String shortName = arg.getTypeName().replace(OrderDto.class.getName(), "OrderDto");
        CiOutput.record(LESSON, "type-reference", """
                ref.getClass().getGenericSuperclass()
                → %s
                타입 인자 → %s

                new ArrayList<OrderDto>().getClass()
                → %s  (객체에는 타입 인자가 없음)""".formatted(parent.getTypeName()
                .replace(OrderDto.class.getName(), "OrderDto"), shortName, plain.getClass().getName()));
    }
}

/** 주문 저장소 */
class OrderRepository {
    String save() {
        return "주문 저장됨";
    }
}

/** private 필드 하나. setter도 생성자 인자도 없다 */
class OrderService {
    private OrderRepository repo;

    String order() {
        return repo.save();
    }

    OrderRepository repo() {
        return repo;
    }
}

/** 코드 container의 대상: repo에 @Inject */
class InjectedOrderService {
    @ReflectionAnnotationTest.Inject
    private OrderRepository repo;

    String order() {
        return repo.save();
    }

    OrderRepository repo() {
        return repo;
    }
}
