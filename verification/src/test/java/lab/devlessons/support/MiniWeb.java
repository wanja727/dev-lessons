package lab.devlessons.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 컨트롤러 몇 개만 든 작은 스프링 부트 웹 앱을 내장 톰캣으로 띄운다.
 * 웹과 JSON에 필요한 자동 설정만 켜고 DB와 배치 설정은 켜지 않는다 (본보기: cookiesessiontoken/SpringSessionPracticeTest).
 *
 * <pre>
 * try (MiniWeb web = MiniWeb.start(OrderController.class)) {
 *     MiniWeb.Reply r = web.get("/orders?page=");
 *     r.status(); r.body();
 * }
 * </pre>
 */
public final class MiniWeb implements AutoCloseable {

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            ErrorMvcAutoConfiguration.class,
    })
    static class App {
    }

    public record Reply(int status, String body) {
    }

    private final ConfigurableApplicationContext context;
    private final int port;
    private final HttpClient http = HttpClient.newHttpClient();

    private MiniWeb(ConfigurableApplicationContext context) {
        this.context = context;
        this.port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
    }

    /** components: 컨트롤러, 인터셉터 설정 등 앱에 넣을 클래스 */
    public static MiniWeb start(Class<?>... components) {
        Class<?>[] sources = new Class<?>[components.length + 1];
        sources[0] = App.class;
        System.arraycopy(components, 0, sources, 1, components.length);
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(sources)
                .properties("server.port=0", "spring.main.banner-mode=off",
                        "server.error.include-message=always")
                .run();
        return new MiniWeb(ctx);
    }

    public ConfigurableApplicationContext context() {
        return context;
    }

    public Reply get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    public Reply postJson(String path, String json) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, UTF_8)).build());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Reply send(HttpRequest request) {
        try {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString(UTF_8));
            return new Reply(res.statusCode(), res.body());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        context.close();
    }
}
