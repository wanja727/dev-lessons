package lab.devlessons.templatemethod;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.BrokenFiles;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.SourceChange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 레슨 "템플릿 메서드 패턴"(template-method)의 주장을 확인한다.
 */
class TemplateMethodTest {

    private static final String LESSON = "template-method";

    record Order(long id, int total, boolean canceled) {
    }

    // ── 코드 exporter, csv, html ──
    abstract static class OrderExporter {
        final String export(List<Order> os) {
            StringBuilder sb = new StringBuilder();
            sb.append(header());
            for (Order o : os) {
                sb.append(row(o));
            }
            sb.append(footer());
            return sb.toString();
        }

        protected abstract String header();

        protected abstract String row(Order o);

        protected String footer() {
            return "";
        }
    }

    static class CsvExporter extends OrderExporter {
        protected String header() {
            return "id,total\n";
        }

        protected String row(Order o) {
            return o.id() + "," + o.total() + "\n";
        }
    }

    static class HtmlExporter extends OrderExporter {
        protected String header() {
            return "<table>";
        }

        protected String row(Order o) {
            return "<tr><td>" + o.id() + "</td></tr>";
        }

        protected String footer() {
            return "</table>";
        }
    }

    private static final List<Order> ORDERS = List.of(new Order(1, 1000, false), new Order(2, 2000, false));

    @Test
    @DisplayName("CSV로 두 건을 내보내면 세 줄, HTML은 <table>로 시작해 </table>로 끝난다")
    void exportFormats() {
        String csv = new CsvExporter().export(ORDERS);
        String html = new HtmlExporter().export(ORDERS);

        assertThat(csv.lines().toList()).containsExactly("id,total", "1,1000", "2,2000");
        assertThat(html).startsWith("<table>").endsWith("</table>");
        CiOutput.record(LESSON, "export", "new CsvExporter().export(orders)\n" + csv
                + "\nnew HtmlExporter().export(orders)\n" + html);
    }

    // ── 취소 주문 빼기: 고치는 파일 ──

    private static String standalone(String name, String header, String row, boolean skip) {
        return """
                class %s {
                  String export(java.util.List<Order> os) {
                    StringBuilder sb = new StringBuilder(%s);
                    for (Order o : os) {
                %s      sb.append(%s);
                    }
                    return sb.toString();
                  }
                }
                """.formatted(name, header, skip ? "      if (o.canceled()) continue;\n" : "", row);
    }

    private static String parent(boolean skip) {
        return """
                abstract class OrderExporter {
                  final String export(java.util.List<Order> os) {
                    StringBuilder sb = new StringBuilder(header());
                    for (Order o : os) {
                %s      sb.append(row(o));
                    }
                    return sb.append(footer()).toString();
                  }
                  protected abstract String header();
                  protected abstract String row(Order o);
                  protected String footer() { return ""; }
                }
                """.formatted(skip ? "      if (o.canceled()) continue;\n" : "");
    }

    private static final String ORDER = "record Order(long id, int total, boolean canceled) {}";
    private static final String CSV = """
            class CsvExporter extends OrderExporter {
              protected String header() { return "id,total\\n"; }
              protected String row(Order o) { return o.id() + "," + o.total() + "\\n"; }
            }
            """;
    private static final String HTML = """
            class HtmlExporter extends OrderExporter {
              protected String header() { return "<table>"; }
              protected String row(Order o) { return "<tr><td>" + o.id() + "</td></tr>"; }
              protected String footer() { return "</table>"; }
            }
            """;

    @Test
    @DisplayName("취소 주문 빼기: 합치기 전에는 두 파일, 템플릿 메서드에서는 부모 한 파일을 고친다")
    void skipCanceledChange() {
        Map<String, String> beforeSplit = Map.of("Order", ORDER,
                "CsvExporter", standalone("CsvExporter", "\"id,total\\n\"", "o.id() + \",\" + o.total() + \"\\n\"", false),
                "HtmlExporter", standalone("HtmlExporter", "\"<table>\"", "\"<tr>\" + o.id() + \"</tr>\"", false));
        Map<String, String> afterSplit = Map.of("Order", ORDER,
                "CsvExporter", standalone("CsvExporter", "\"id,total\\n\"", "o.id() + \",\" + o.total() + \"\\n\"", true),
                "HtmlExporter", standalone("HtmlExporter", "\"<table>\"", "\"<tr>\" + o.id() + \"</tr>\"", true));
        Map<String, String> before = Map.of("Order", ORDER, "OrderExporter", parent(false), "CsvExporter", CSV,
                "HtmlExporter", HTML);
        Map<String, String> after = new HashMap<>(before);
        after.put("OrderExporter", parent(true));

        assertThat(BrokenFiles.compile(afterSplit).ok()).isTrue();
        assertThat(BrokenFiles.compile(after).ok()).isTrue();
        SourceChange split = SourceChange.between(beforeSplit, afterSplit);
        SourceChange template = SourceChange.between(before, after);

        assertThat(split.changed()).containsExactly("CsvExporter", "HtmlExporter");
        assertThat(template.changed()).containsExactly("OrderExporter");
        CiOutput.record(LESSON, "skip", "[취소된 주문은 빼기]\n\n합치기 전 (두 클래스에 같은 순서)\n" + split.describe()
                + "\n\n템플릿 메서드\n" + template.describe());
    }

    // ── 막아 두는 것: 컴파일 ──

    @Test
    @DisplayName("final export를 오버라이드하면 컴파일 오류, default 메서드에 final을 붙여도 오류, default 뼈대는 덮어쓸 수 있다")
    void finalSkeleton() {
        Map<String, String> overrideFinal = Map.of("Order", ORDER, "OrderExporter", parent(false), "CsvExporter", """
                class CsvExporter extends OrderExporter {
                  protected String header() { return ""; }
                  protected String row(Order o) { return ""; }
                  String export(java.util.List<Order> os) { return "순서를 바꿨다"; }
                }
                """);
        Map<String, String> finalDefault = Map.of("Order", ORDER, "OrderExporter", """
                interface OrderExporter {
                  final default String export(java.util.List<Order> os) { return ""; }
                }
                """);
        Map<String, String> defaultOverridden = Map.of("Order", ORDER, "OrderExporter", """
                interface OrderExporter {
                  default String export(java.util.List<Order> os) { return header(); }
                  String header();
                }
                """, "CsvExporter", """
                class CsvExporter implements OrderExporter {
                  public String header() { return ""; }
                  public String export(java.util.List<Order> os) { return "순서를 바꿨다"; }
                }
                """);

        BrokenFiles a = BrokenFiles.compile(overrideFinal);
        BrokenFiles b = BrokenFiles.compile(finalDefault);
        BrokenFiles c = BrokenFiles.compile(defaultOverridden);

        assertThat(a.files()).containsExactly("CsvExporter");
        assertThat(a.messages().get(0)).contains("cannot override export");
        assertThat(b.files()).containsExactly("OrderExporter");
        assertThat(c.ok()).isTrue();
        CiOutput.record(LESSON, "final", "[추상 클래스의 final export를 자식이 오버라이드]\nerror: "
                + a.messages().get(0).substring("CsvExporter: ".length())
                + "\n\n[인터페이스의 default 메서드에 final]\nerror: " + b.messages().get(0).substring("OrderExporter: ".length())
                + "\n\n[final 없는 default 뼈대를 구현이 덮어쓰기]\n→ 컴파일 성공 (순서를 바꿀 수 있다)");
    }

    // ── 실무: OncePerRequestFilter ──

    static class CountingFilter extends OncePerRequestFilter {
        int calls = 0;

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            calls++;
            chain.doFilter(req, res);
        }
    }

    @Test
    @DisplayName("OncePerRequestFilter: 같은 요청 안에서 필터가 다시 불려도 doFilterInternal은 한 번")
    void oncePerRequest() throws Exception {
        CountingFilter filter = new CountingFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse res = new MockHttpServletResponse();
        // 체인 안에서 같은 필터를 한 번 더 지나게 한다 (포워드처럼)
        FilterChain inner = (rq, rs) -> {
        };
        FilterChain again = (rq, rs) -> filter.doFilter(rq, rs, inner);

        filter.doFilter(req, res, again);

        assertThat(filter.calls).isEqualTo(1);
        CiOutput.record(LESSON, "once", "같은 요청에서 doFilter가 두 번 불림\n→ doFilterInternal 실행: " + filter.calls + "번");
    }

    // 흔한 실수: 뼈대인 doFilter를 오버라이드한다 (스프링 6.2에서 doFilter는 final이 아니다)
    static class OverridingFilter extends OncePerRequestFilter {
        int internalCalls = 0;
        int bodyRuns = 0;

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
            internalCalls++;
        }

        @Override
        public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res,
                FilterChain chain) throws jakarta.servlet.ServletException, java.io.IOException {
            bodyRuns++;
            chain.doFilter(req, res);
        }
    }

    @Test
    @DisplayName("doFilter는 final이 아니라서 오버라이드하면 컴파일되고, 한 번만 실행하는 뼈대를 건너뛰어 같은 요청에서 두 번 돈다")
    void overridingDoFilterSkipsSkeleton() throws Exception {
        java.lang.reflect.Method m = OncePerRequestFilter.class.getMethod("doFilter",
                jakarta.servlet.ServletRequest.class, jakarta.servlet.ServletResponse.class, FilterChain.class);
        OverridingFilter filter = new OverridingFilter();
        FilterChain inner = (rq, rs) -> {
        };
        FilterChain again = (rq, rs) -> filter.doFilter(rq, rs, inner);

        filter.doFilter(new MockHttpServletRequest("GET", "/orders"), new MockHttpServletResponse(), again);

        assertThat(java.lang.reflect.Modifier.isFinal(m.getModifiers())).isFalse();
        assertThat(filter.bodyRuns).isEqualTo(2);
        assertThat(filter.internalCalls).isZero();
        CiOutput.record(LESSON, "filter-override", "doFilter를 오버라이드한 필터, 같은 요청에서 두 번 불림\n"
                + "→ doFilter 몸체 실행: " + filter.bodyRuns + "번\n→ doFilterInternal 실행: " + filter.internalCalls + "번");
    }
}
