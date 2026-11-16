package lab.devlessons.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.stream.Collectors;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 레슨 "스프링 빈과 의존성 주입"의 주장을 실제 스프링 컨테이너로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class SpringBeansTest {

    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("컨테이너에서 같은 빈을 두 번 꺼내면 같은 객체다 (기본은 싱글톤)")
    void singletonByDefault() {
        CoffeeService a = context.getBean(CoffeeService.class);
        CoffeeService b = context.getBean(CoffeeService.class);

        assertThat(a).isSameAs(b);
    }

    @Test
    @DisplayName("생성자 주입: 서비스가 받은 객체는 컨테이너가 관리하는 바로 그 빈이다")
    void constructorInjection() {
        CoffeeService service = context.getBean(CoffeeService.class);

        assertThat(service.sender()).isSameAs(context.getBean(NoticeSender.class));
        assertThat(service.sender()).isInstanceOf(SmsNoticeSender.class);   // 인터페이스 자리에 구현이 들어옴
        assertThat(service.menu()).isSameAs(context.getBean(CoffeeMenuRepository.class));
    }

    @Test
    @DisplayName("컨테이너는 필요한 객체를 먼저 만들고, 그것을 넣어 다음 객체를 만든다 (출력 공개)")
    void creationOrder() {
        // 테스트마다 컨테이너가 새로 뜰 수 있어서, 처음 나온 줄만 남긴다
        var lines = CreationLog.lines().stream().distinct().toList();
        int repo = lines.indexOf("CoffeeMenuRepository 생성");
        int sender = lines.indexOf("SmsNoticeSender 생성");
        int service = lines.indexOf("CoffeeService 생성 (CoffeeMenuRepository, NoticeSender를 받음)");
        int controller = lines.indexOf("CoffeeController 생성 (CoffeeService를 받음)");

        assertThat(repo).isLessThan(service);
        assertThat(sender).isLessThan(service);
        assertThat(service).isLessThan(controller);
        CiOutput.record("spring-beans", "creation", String.join("\n", lines));
    }

    @Test
    @DisplayName("new로 직접 만든 객체는 컨테이너가 모른다: 필드 주입이 일어나지 않아 null")
    void newObjectIsNotInjected() {
        FieldInjectedService bean = context.getBean(FieldInjectedService.class);
        FieldInjectedService manual = new FieldInjectedService();

        assertThat(bean.sender()).isNotNull();
        assertThat(manual.sender()).isNull();
    }

    @Test
    @DisplayName("생성자 주입이면 스프링 없이도 가짜 객체를 넣어 테스트할 수 있다")
    void constructorInjectionWithoutSpring() {
        NoticeSender fake = (to, message) -> "가짜:" + to + ":" + message;
        CoffeeService service = new CoffeeService(new CoffeeMenuRepository(), fake);

        assertThat(service.order("kim", "latte")).isEqualTo("가짜:kim:latte 5000원 주문 완료");
    }

    @Test
    @DisplayName("싱글톤 빈의 필드는 모든 요청이 함께 쓴다: 요청 두 번이면 주문 수가 두 번 올라간다")
    void singletonStateIsShared() throws Exception {
        CoffeeService service = context.getBean(CoffeeService.class);
        int before = service.orderCount();

        mvc.perform(get("/beans/coffee").param("user", "kim").param("menu", "americano")).andExpect(status().isOk());
        mvc.perform(get("/beans/coffee").param("user", "lee").param("menu", "latte")).andExpect(status().isOk());

        assertThat(service.orderCount()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("요청은 컨트롤러 → 서비스 → 리포지토리를 거쳐 응답이 된다 (출력 공개)")
    void requestFlow() throws Exception {
        String body = mvc.perform(get("/beans/coffee").param("user", "kim").param("menu", "latte"))
                .andExpect(status().isOk())
                .andExpect(content().string("[SMS → kim] latte 5000원 주문 완료"))
                .andReturn().getResponse().getContentAsString();

        CiOutput.record("spring-beans", "request", "GET /beans/coffee?user=kim&menu=latte\n→ " + body);
    }

    @Test
    @DisplayName("프로토타입 빈은 꺼낼 때마다 새 객체다")
    void prototypeIsNewEachTime() {
        ShoppingCart a = context.getBean(ShoppingCart.class);
        ShoppingCart b = context.getBean(ShoppingCart.class);
        a.add("latte");

        assertThat(a).isNotSameAs(b);
        assertThat(b.items()).isEmpty();
    }

    @Test
    @DisplayName("이 레슨 패키지에서 컨테이너가 만든 빈 목록과 스코프 (출력 공개)")
    void beanList() {
        var factory = ((ConfigurableApplicationContext) context).getBeanFactory();
        String list = Arrays.stream(context.getBeanDefinitionNames())
                .filter(n -> {
                    Class<?> type = context.getType(n);
                    return type != null && type.getPackageName().equals("lab.devlessons.beans");
                })
                .sorted()
                .map(n -> {
                    String scope = factory.getBeanDefinition(n).getScope();
                    return n + " → " + context.getType(n).getSimpleName() + " (" + (scope.isEmpty() ? "singleton" : scope) + ")";
                })
                .collect(Collectors.joining("\n"));

        assertThat(list).contains("coffeeService → CoffeeService (singleton)").contains("shoppingCart → ShoppingCart (prototype)");
        CiOutput.record("spring-beans", "beans", list);
    }
}
