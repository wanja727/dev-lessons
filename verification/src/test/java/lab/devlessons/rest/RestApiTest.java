package lab.devlessons.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 레슨 "HTTP와 REST API 설계"의 주장을 실제 스프링 MVC(MockMvc)로 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class RestApiTest {

    static final String LESSON = "rest-api";
    static final String BASE = "/api/v1/products";
    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired ProductApiController api;

    @BeforeEach
    void setUp() {
        api.reset();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder req) throws Exception {
        MockHttpServletResponse res = mvc.perform(req).andReturn().getResponse();
        res.setCharacterEncoding("UTF-8");
        return res;
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** "HTTP/1.1 201" 같은 첫 줄과 주요 헤더, 본문 */
    private static String show(String request, MockHttpServletResponse res) throws Exception {
        StringBuilder sb = new StringBuilder("> ").append(request).append('\n');
        sb.append("< ").append(res.getStatus());
        for (String h : List.of("Location", "Allow", "Content-Type")) {
            if (res.getHeader(h) != null) {
                sb.append("\n< ").append(h).append(": ").append(res.getHeader(h));
            }
        }
        String body = res.getContentAsString();
        if (!body.isEmpty()) {
            sb.append("\n").append(body);
        }
        return sb.toString();
    }

    @Test
    @DisplayName("POST를 두 번 보내면 상품이 두 개 생긴다(201, Location). PUT을 두 번 보내면 하나이고 결과가 같다")
    void postIsNotIdempotentButPutIs() throws Exception {
        String body = "{\"name\":\"커피\",\"price\":4500}";
        MockHttpServletResponse p1 = call(json(post(BASE), body));
        MockHttpServletResponse p2 = call(json(post(BASE), body));
        int afterPosts = api.count();

        api.reset();
        MockHttpServletResponse u1 = call(json(put(BASE + "/7"), body));
        MockHttpServletResponse u2 = call(json(put(BASE + "/7"), body));
        int afterPuts = api.count();

        assertThat(p1.getStatus()).isEqualTo(201);
        assertThat(p1.getHeader("Location")).isEqualTo(BASE + "/1");
        assertThat(p2.getHeader("Location")).isEqualTo(BASE + "/2");
        assertThat(afterPosts).isEqualTo(2);
        assertThat(u1.getStatus()).isEqualTo(201);
        assertThat(u2.getStatus()).isEqualTo(200);
        assertThat(u1.getContentAsString()).isEqualTo(u2.getContentAsString());
        assertThat(afterPuts).isEqualTo(1);
        CiOutput.record(LESSON, "post-vs-put", String.join("\n\n",
                show("POST " + BASE + " (1번째)", p1), show("POST " + BASE + " (2번째, 같은 본문)", p2),
                "상품 수: " + afterPosts,
                show("PUT " + BASE + "/7 (1번째)", u1), show("PUT " + BASE + "/7 (2번째, 같은 본문)", u2),
                "상품 수: " + afterPuts));
    }

    @Test
    @DisplayName("DELETE를 두 번 보내면 두 번째는 404지만, 서버 상태(상품 없음)는 같다")
    void deleteTwice() throws Exception {
        call(json(put(BASE + "/1"), "{\"name\":\"커피\",\"price\":4500}"));

        MockHttpServletResponse d1 = call(delete(BASE + "/1"));
        MockHttpServletResponse d2 = call(delete(BASE + "/1"));

        assertThat(d1.getStatus()).isEqualTo(204);
        assertThat(d2.getStatus()).isEqualTo(404);
        assertThat(api.count()).isZero();
    }

    @Test
    @DisplayName("없는 자원은 404, 잘못된 입력은 400을 RFC 9457 Problem Details(application/problem+json)로 돌려준다")
    void errorsUseProblemDetails() throws Exception {
        MockHttpServletResponse notFound = call(get(BASE + "/99"));
        MockHttpServletResponse invalid = call(json(post(BASE), "{\"name\":\"커피\",\"price\":-1}"));

        assertThat(notFound.getStatus()).isEqualTo(404);
        assertThat(notFound.getContentType()).startsWith("application/problem+json");
        JsonNode nf = JSON.readTree(notFound.getContentAsString());
        assertThat(nf.get("status").asInt()).isEqualTo(404);
        assertThat(nf.get("productId").asLong()).isEqualTo(99);
        assertThat(invalid.getStatus()).isEqualTo(400);
        assertThat(JSON.readTree(invalid.getContentAsString()).get("errors").get("price").asText()).contains("0 이상");
        CiOutput.record(LESSON, "problem", show("GET " + BASE + "/99", notFound) + "\n\n"
                + show("POST " + BASE + " {\"name\":\"커피\",\"price\":-1}", invalid));
    }

    @Test
    @DisplayName("지원하지 않는 메서드는 405와 Allow 헤더")
    void methodNotAllowed() throws Exception {
        MockHttpServletResponse res = call(post(BASE + "/1"));

        assertThat(res.getStatus()).isEqualTo(405);
        assertThat(res.getHeader("Allow")).contains("GET", "PUT", "DELETE");
    }

    private List<Long> ids(MockHttpServletResponse res) throws Exception {
        List<Long> out = new ArrayList<>();
        JSON.readTree(res.getContentAsString()).get("items").forEach(n -> out.add(n.get("id").asLong()));
        return out;
    }

    @Test
    @DisplayName("offset 페이지네이션은 페이지 사이에 새 글이 생기면 앞 페이지의 항목이 다음 페이지에 또 나온다. cursor 방식은 중복이 없다")
    void offsetDuplicatesCursorDoesNot() throws Exception {
        for (int i = 1; i <= 6; i++) {
            call(json(post(BASE), "{\"name\":\"상품" + i + "\",\"price\":1000}"));
        }
        // offset
        List<Long> offsetPage0 = ids(call(get(BASE).param("page", "0").param("size", "3")));
        call(json(post(BASE), "{\"name\":\"새 상품\",\"price\":1000}")); // 그 사이 새 상품 (id 7)
        List<Long> offsetPage1 = ids(call(get(BASE).param("page", "1").param("size", "3")));

        api.reset();
        for (int i = 1; i <= 6; i++) {
            call(json(post(BASE), "{\"name\":\"상품" + i + "\",\"price\":1000}"));
        }
        // cursor
        MockHttpServletResponse first = call(get(BASE).param("size", "3"));
        List<Long> cursorPage0 = ids(first);
        long next = JSON.readTree(first.getContentAsString()).get("nextCursor").asLong();
        call(json(post(BASE), "{\"name\":\"새 상품\",\"price\":1000}"));
        List<Long> cursorPage1 = ids(call(get(BASE).param("cursor", String.valueOf(next)).param("size", "3")));

        assertThat(offsetPage0).containsExactly(6L, 5L, 4L);
        assertThat(offsetPage1).containsExactly(4L, 3L, 2L); // 4가 또 나온다
        assertThat(cursorPage0).containsExactly(6L, 5L, 4L);
        assertThat(cursorPage1).containsExactly(3L, 2L, 1L);
        CiOutput.record(LESSON, "pagination", "[상품 6개, 최신순 3개씩. 1페이지를 본 뒤 새 상품 1개 등록]\n"
                + "offset: 1페이지 " + offsetPage0 + " → 2페이지 " + offsetPage1 + "  (4번이 또 나옴)\n"
                + "cursor: 1페이지 " + cursorPage0 + " → 2페이지(cursor=" + next + ") " + cursorPage1);
    }
}
