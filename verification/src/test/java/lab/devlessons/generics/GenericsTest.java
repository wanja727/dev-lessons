package lab.devlessons.generics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "제네릭"(J11)의 주장을 검증한다. 컴파일되는지는 테스트 안에서 javac(자바 21)로 확인한다.
 */
class GenericsTest {

    private static final String LESSON = "generics";

    @Test
    @DisplayName("코드 predict: List<String>과 List<Long>의 getClass()는 같다")
    void predict() {
        List<String> names = new ArrayList<>();
        List<Long> ids = new ArrayList<>();

        assertThat(names.getClass() == ids.getClass()).isTrue();
        CiOutput.record(LESSON, "predict", String.valueOf(names.getClass() == ids.getClass())
                + "\n\n(둘 다 " + names.getClass().getName() + ")");
    }

    @Test
    @DisplayName("제네릭 없이 쓰면 잘못 넣은 것이 꺼내는 줄에서 ClassCastException이 된다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rawList() {
        List list = new ArrayList();
        list.add("kim");
        list.add(10L);

        assertThatThrownBy(() -> {
            String name = (String) list.get(1);
        }).isInstanceOf(ClassCastException.class);
        String message = null;
        try {
            String name = (String) list.get(1);
        } catch (ClassCastException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "raw", "list.add(10L)          → 지나감\n(String) list.get(1)   → ClassCastException: "
                + message.replaceAll(" \\(.*$", ""));
    }

    @Test
    @DisplayName("List<String>에 Long을 넣는 코드는 컴파일되지 않는다")
    void typedListRejectsWrongType() {
        JavaSource.Result r = JavaSource.compile("Shop", """
                import java.util.*;
                public class Shop {
                  void run() {
                    List<String> list = new ArrayList<>();
                    list.add("kim");
                    list.add(10L);
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        CiOutput.record(LESSON, "typed", "list.add(10L) → error: " + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("로 타입에 대입하면 넣는 줄은 지나가고, 숨은 형변환이 있는 꺼내는 줄에서 ClassCastException이 난다")
    @SuppressWarnings({"rawtypes", "unchecked"})
    void heapPollution() {
        List<String> names = new ArrayList<>();
        List raw = names;
        raw.add(10L);

        assertThat(names).hasSize(1);
        assertThatThrownBy(() -> {
            String first = names.get(0);
        }).isInstanceOf(ClassCastException.class);

        JavaSource.Result warn = JavaSource.compile("Shop", """
                import java.util.*;
                public class Shop {
                  void run() {
                    List<String> names = new ArrayList<>();
                    List raw = names;
                    raw.add(10L);
                  }
                }
                """);
        assertThat(warn.ok()).isTrue();
        assertThat(warn.warnings()).anyMatch(w -> w.contains("unchecked"));
        CiOutput.record(LESSON, "pollution", """
                List raw = names;        → 지나감
                raw.add(10L);            → 지나감 (컴파일러 경고: %s)
                String first = names.get(0);
                → ClassCastException""".formatted(warn.warnings().stream()
                .filter(w -> w.contains("unchecked")).findFirst().orElse("").lines().findFirst().orElse("")));
    }

    @Test
    @DisplayName("타입 인자만 다른 오버로딩은 지우고 나면 같아져서 컴파일되지 않는다")
    void erasureClash() {
        JavaSource.Result r = JavaSource.compile("Repo", """
                import java.util.*;
                public class Repo {
                  void save(List<String> names) {}
                  void save(List<Long> ids) {}
                }
                """);
        assertThat(r.ok()).isFalse();
        assertThat(r.errors()).anyMatch(e -> e.contains("same erasure"));
        CiOutput.record(LESSON, "erasure", "error: " + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("new T()와 instanceof List<String>은 컴파일되지 않는다")
    void erasureLimits() {
        JavaSource.Result newT = JavaSource.compile("Box", """
                public class Box<T> { T make() { return new T(); } }
                """);
        JavaSource.Result instanceOf = JavaSource.compile("Check", """
                import java.util.*;
                public class Check { boolean is(Object o) { return o instanceof List<String>; } }
                """);
        assertThat(newT.ok()).isFalse();
        assertThat(instanceOf.ok()).isFalse();
    }

    @Test
    @DisplayName("Comparable로 범위를 제한한 제네릭 메서드는 List<String>을 받고 List<Object>는 받지 않는다")
    void boundedType() {
        String max = """
                import java.util.*;
                public class Util {
                  static <T extends Comparable<T>> T max(List<T> list) {
                    T best = list.get(0);
                    for (T t : list) if (t.compareTo(best) > 0) best = t;
                    return best;
                  }
                """;
        JavaSource.Result strings = JavaSource.compile("Util", max
                + "  void run() { max(List.of(\"b\", \"a\")); } }");
        JavaSource.Result objects = JavaSource.compile("Util", max
                + "  void run() { List<Object> os = new ArrayList<>(); max(os); } }");

        assertThat(strings.ok()).isTrue();
        assertThat(objects.ok()).isFalse();
    }

    @Test
    @DisplayName("List<Integer>를 List<Number>에 대입하면 컴파일되지 않는다")
    void invariance() {
        JavaSource.Result r = JavaSource.compile("Shop", """
                import java.util.*;
                public class Shop {
                  void run() {
                    List<Integer> ints = new ArrayList<>();
                    List<Number> nums = ints;
                  }
                }
                """);
        assertThat(r.ok()).isFalse();
        CiOutput.record(LESSON, "invariant", "List<Number> nums = ints;\n→ error: "
                + r.errors().get(0).lines().findFirst().orElse(""));
    }

    @Test
    @DisplayName("배열은 대입을 허용하고 실행 중에 ArrayStoreException으로 막는다")
    void arrayStore() {
        Number[] nums = new Integer[1];
        assertThatThrownBy(() -> nums[0] = 3.14).isInstanceOf(ArrayStoreException.class);
        String message = null;
        try {
            nums[0] = 3.14;
        } catch (ArrayStoreException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "array", """
                Number[] nums = new Integer[1];  → 컴파일됨
                nums[0] = 3.14;
                → ArrayStoreException: %s""".formatted(message));
    }

    /** 위젯의 여덟 줄. 앞에 src와 dest가 선언돼 있다 */
    static final String WILDCARD_HEADER = """
            import java.util.*;
            public class W {
              static double sum(List<? extends Number> list) { return 0; }
              static double sumExact(List<Number> list) { return 0; }
              void run(List<? extends Number> src, List<? super Integer> dest) {
            """;

    static boolean compilesInside(String line) {
        return JavaSource.compile("W", WILDCARD_HEADER + "    " + line + "\n  }\n}\n").ok();
    }

    @Test
    @DisplayName("와일드카드 여덟 줄이 위젯에 적은 대로 컴파일되거나 컴파일되지 않는다")
    void wildcardLines() {
        Map<String, Boolean> expected = new LinkedHashMap<>();
        expected.put("Number n = src.get(0);", true);
        expected.put("src.add(1);", false);
        expected.put("Integer i = src.get(0);", false);
        expected.put("dest.add(1);", true);
        expected.put("Integer j = dest.get(0);", false);
        expected.put("Object o = dest.get(0);", true);
        expected.put("sum(new ArrayList<Double>());", true);
        expected.put("sumExact(new ArrayList<Double>());", false);

        StringBuilder text = new StringBuilder();
        expected.forEach((line, ok) -> {
            boolean actual = compilesInside(line);
            assertThat(actual).as(line).isEqualTo(ok);
            text.append(actual ? "컴파일됨   " : "컴파일 오류 ").append(line).append('\n');
        });
        CiOutput.record(LESSON, "wildcard", text.toString().strip());
    }

    @Test
    @DisplayName("? super Long에 List<Number>는 되고 List<Integer>는 안 된다")
    void superBound() {
        String header = """
                import java.util.*;
                public class S {
                  static void addDefaults(List<? super Long> ids) { ids.add(0L); }
                """;
        assertThat(JavaSource.compile("S", header + "  void run() { addDefaults(new ArrayList<Number>()); } }").ok())
                .isTrue();
        assertThat(JavaSource.compile("S", header + "  void run() { addDefaults(new ArrayList<Integer>()); } }").ok())
                .isFalse();
    }

    // ── 실무 구역 ──
    record OrderDto(long id, int price) {
    }

    record ApiResponse<T>(T data, String message) {
        static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(data, "OK");
        }
    }

    static final String JSON = "[{\"id\":1,\"price\":10000},{\"id\":2,\"price\":20000}]";

    @Test
    @DisplayName("JSON 배열을 List.class로 읽으면 원소가 LinkedHashMap이고, 꺼내면 ClassCastException")
    @SuppressWarnings("unchecked")
    void jacksonRawList() throws Exception {
        ObjectMapper json = new ObjectMapper();
        List<OrderDto> orders = json.readValue(JSON, List.class);

        assertThat((Object) orders.get(0)).isInstanceOf(LinkedHashMap.class);
        assertThatThrownBy(() -> {
            OrderDto first = orders.get(0);
        }).isInstanceOf(ClassCastException.class);

        String message = null;
        try {
            OrderDto first = orders.get(0);
        } catch (ClassCastException e) {
            message = e.getMessage().replaceAll(" \\(.*$", "")
                    .replace("lab.devlessons.generics.GenericsTest$", "");
        }
        List<OrderDto> fixed = json.readValue(JSON, new TypeReference<List<OrderDto>>() {
        });
        assertThat(fixed.get(0)).isEqualTo(new OrderDto(1, 10000));

        CiOutput.record(LESSON, "jackson", """
                readValue(json, List.class)
                → 원소의 클래스: %s
                → OrderDto first = orders.get(0);
                  ClassCastException: %s

                readValue(json, new TypeReference<List<OrderDto>>() {})
                → 원소의 클래스: %s
                → orders.get(0): %s""".formatted(((Object) orders.get(0)).getClass().getName(), message,
                fixed.get(0).getClass().getSimpleName(), fixed.get(0)));
    }

    @Test
    @DisplayName("ApiResponse<T>는 data 안에 주문이 든 JSON으로 쓰인다")
    void apiResponse() throws Exception {
        ObjectMapper json = new ObjectMapper();
        String one = json.writeValueAsString(ApiResponse.ok(new OrderDto(1, 10000)));
        String many = json.writeValueAsString(ApiResponse.ok(List.of(new OrderDto(1, 10000))));

        assertThat(one).isEqualTo("{\"data\":{\"id\":1,\"price\":10000},\"message\":\"OK\"}");
        assertThat(many).isEqualTo("{\"data\":[{\"id\":1,\"price\":10000}],\"message\":\"OK\"}");
        CiOutput.record(LESSON, "api-response", "ApiResponse<OrderDto>\n" + one
                + "\n\nApiResponse<List<OrderDto>>\n" + many);
    }
}
