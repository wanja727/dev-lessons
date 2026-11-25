package lab.devlessons.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "스트림과 Optional"(J13)의 주장을 검증한다. 출력 대신 리스트에 기록해서 실행 순서를 본다.
 */
class StreamOptionalTest {

    private static final String LESSON = "stream-optional";

    enum Status { CREATED, PAID }

    record Order(long id, long memberId, int price, Status status) {
        int getPrice() {
            return price;
        }

        long getMemberId() {
            return memberId;
        }

        Status getStatus() {
            return status;
        }
    }

    @Test
    @DisplayName("코드 predict: 최종 연산이 없으면 아무것도 실행되지 않고, toList를 붙이면 원소마다 filter와 map이 차례로 돈다")
    void lazyEvaluation() {
        List<String> log = new ArrayList<>();
        Stream<String> notRun = Stream.of("A", "B", "C")
                .filter(s -> {
                    log.add("filter " + s);
                    return true;
                })
                .map(s -> {
                    log.add("map " + s);
                    return s.toLowerCase();
                });
        List<String> beforeTerminal = List.copyOf(log);

        Stream.of("A", "B", "C")
                .filter(s -> {
                    log.add("filter " + s);
                    return true;
                })
                .map(s -> {
                    log.add("map " + s);
                    return s.toLowerCase();
                })
                .toList();

        assertThat(notRun).isNotNull();
        assertThat(beforeTerminal).isEmpty();
        assertThat(log).containsExactly("filter A", "map A", "filter B", "map B", "filter C", "map C");
        CiOutput.record(LESSON, "predict", "(아무것도 출력되지 않음)");
        CiOutput.record(LESSON, "with-terminal", String.join("\n", log));
    }

    @Test
    @DisplayName("코드 first: findFirst는 하나를 찾으면 멈춘다 (filter A, filter B, map B)")
    void shortCircuit() {
        List<String> log = new ArrayList<>();
        Optional<String> first = Stream.of("A", "B", "C")
                .filter(s -> {
                    log.add("filter " + s);
                    return !s.equals("A");
                })
                .map(s -> {
                    log.add("map " + s);
                    return s.toLowerCase();
                })
                .findFirst();

        assertThat(log).containsExactly("filter A", "filter B", "map B");
        assertThat(first).contains("b");
        CiOutput.record(LESSON, "first", String.join("\n", log) + "\n\n결과 → " + first);
    }

    @Test
    @DisplayName("위젯: 다섯 원소에 filter(A 아님), map(소문자), limit(2)를 걸면 D와 E는 처리되지 않는다")
    void widgetPipeline() {
        List<String> log = new ArrayList<>();
        List<String> result = Stream.of("A", "B", "C", "D", "E")
                .filter(s -> {
                    log.add("filter " + s);
                    return !s.equals("A");
                })
                .map(s -> {
                    log.add("map " + s);
                    return s.toLowerCase();
                })
                .limit(2)
                .toList();

        assertThat(result).containsExactly("b", "c");
        assertThat(log).containsExactly("filter A", "filter B", "map B", "filter C", "map C");
        CiOutput.record(LESSON, "widget", String.join("\n", log) + "\n\n결과 → " + result);
    }

    @Test
    @DisplayName("코드 pipeline: 결제된 주문의 금액만 뽑고 원본은 그대로다")
    void pipeline() {
        List<Order> orders = List.of(new Order(1, 1, 10000, Status.PAID), new Order(2, 1, 5000, Status.CREATED),
                new Order(3, 2, 20000, Status.PAID));
        List<Integer> prices = orders.stream()
                .filter(o -> o.getStatus() == Status.PAID)
                .map(Order::getPrice)
                .toList();

        assertThat(prices).containsExactly(10000, 20000);
        assertThat(orders).hasSize(3);
        CiOutput.record(LESSON, "pipeline", "prices → " + prices + "\norders.size() → " + orders.size());
    }

    @Test
    @DisplayName("최종 연산을 부른 스트림에 다시 최종 연산을 부르면 IllegalStateException")
    void streamUsedOnce() {
        Stream<String> s = Stream.of("A", "B");
        s.count();
        assertThatThrownBy(s::count).isInstanceOf(IllegalStateException.class);
        String message = null;
        try {
            s.count();
        } catch (IllegalStateException e) {
            message = e.getMessage();
        }
        CiOutput.record(LESSON, "once", "s.count(); s.count();\n→ IllegalStateException: " + message);
    }

    @Test
    @DisplayName("Stream.toList()의 결과는 고칠 수 없다")
    void toListUnmodifiable() {
        List<Integer> list = Stream.of(1, 2).toList();
        assertThatThrownBy(() -> list.add(3)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("toMap은 키가 겹치면 IllegalStateException, groupingBy는 같은 키의 원소를 리스트로 묶는다")
    void collectors() {
        List<Order> orders = List.of(new Order(1, 1, 10000, Status.PAID), new Order(2, 1, 5000, Status.PAID),
                new Order(3, 2, 20000, Status.PAID));

        Map<Long, List<Order>> grouped = orders.stream().collect(Collectors.groupingBy(Order::getMemberId));
        assertThat(grouped.get(1L)).hasSize(2);
        assertThatThrownBy(() -> orders.stream().collect(Collectors.toMap(Order::getMemberId, Order::getPrice)))
                .isInstanceOf(IllegalStateException.class);
        String message = null;
        try {
            orders.stream().collect(Collectors.toMap(Order::getMemberId, Order::getPrice));
        } catch (IllegalStateException e) {
            message = e.getMessage();
        }
        int sum = orders.stream().mapToInt(Order::getPrice).sum();
        assertThat(sum).isEqualTo(35000);
        CiOutput.record(LESSON, "collect", """
                groupingBy(Order::getMemberId)
                → 키 1의 주문 수 %d, 키 2의 주문 수 %d

                toMap(Order::getMemberId, Order::getPrice)
                → IllegalStateException: %s

                mapToInt(Order::getPrice).sum() → %d""".formatted(grouped.get(1L).size(), grouped.get(2L).size(),
                message, sum));
    }

    @Test
    @DisplayName("빈 Optional의 get은 NoSuchElementException, Optional.of(null)은 NullPointerException, map은 빈 채로 넘어간다")
    void optionalBasics() {
        Optional<String> empty = Optional.empty();

        assertThatThrownBy(empty::get).isInstanceOf(NoSuchElementException.class);
        assertThat(empty.orElse("없음")).isEqualTo("없음");
        assertThatThrownBy(() -> Optional.of(null)).isInstanceOf(NullPointerException.class);
        assertThat(Optional.ofNullable(null).map(Object::toString).orElse("기본")).isEqualTo("기본");
        CiOutput.record(LESSON, "optional", """
                Optional.empty().get()          → NoSuchElementException
                Optional.empty().orElse("없음")  → 없음
                Optional.of(null)               → NullPointerException
                Optional.ofNullable(null)
                    .map(Object::toString)
                    .orElse("기본")              → 기본""");
    }

    static int createCalls;

    static String create() {
        createCalls++;
        return "새 회원";
    }

    @Test
    @DisplayName("값이 있을 때 orElse(f())는 f를 1번, orElseGet(() -> f())는 0번 실행한다. 없을 때는 둘 다 1번")
    void orElseVersusOrElseGet() {
        int[][] table = new int[2][2];
        Optional<String> found = Optional.of("kim");
        Optional<String> missing = Optional.empty();

        createCalls = 0;
        found.orElse(create());
        table[0][0] = createCalls;
        createCalls = 0;
        found.orElseGet(() -> create());
        table[0][1] = createCalls;
        createCalls = 0;
        missing.orElse(create());
        table[1][0] = createCalls;
        createCalls = 0;
        missing.orElseGet(() -> create());
        table[1][1] = createCalls;

        assertThat(table[0][0]).isEqualTo(1);
        assertThat(table[0][1]).isZero();
        assertThat(table[1][0]).isEqualTo(1);
        assertThat(table[1][1]).isEqualTo(1);
        CiOutput.record(LESSON, "or-else", """
                createMember 호출 횟수
                                 orElse   orElseGet
                회원을 찾았을 때     %d        %d
                못 찾았을 때         %d        %d""".formatted(table[0][0], table[0][1], table[1][0], table[1][1]));
    }

    /** 실무 구역: save 호출을 세는 가짜 리포지토리 */
    static class FakeMemberRepository {
        final Map<String, String> members = new HashMap<>();
        int saveCalls;

        Optional<String> findByEmail(String email) {
            return Optional.ofNullable(members.get(email));
        }

        String save(String email) {
            saveCalls++;
            members.put(email, "회원(" + email + ")");
            return members.get(email);
        }
    }

    @Test
    @DisplayName("이미 있는 회원에 대해 orElse(repo.save(...))를 세 번 실행하면 save가 세 번, orElseGet이면 0번")
    void orElseSaveMistake() {
        FakeMemberRepository bad = new FakeMemberRepository();
        bad.members.put("kim@a.com", "회원(kim@a.com)");
        for (int i = 0; i < 3; i++) {
            bad.findByEmail("kim@a.com").orElse(bad.save("kim@a.com"));
        }
        FakeMemberRepository good = new FakeMemberRepository();
        good.members.put("kim@a.com", "회원(kim@a.com)");
        for (int i = 0; i < 3; i++) {
            good.findByEmail("kim@a.com").orElseGet(() -> good.save("kim@a.com"));
        }

        assertThat(bad.saveCalls).isEqualTo(3);
        assertThat(good.saveCalls).isZero();
        CiOutput.record(LESSON, "save", """
                이미 가입한 kim@a.com으로 세 번 요청
                orElse(repo.save(...))         → save 호출 %d번
                orElseGet(() -> repo.save(...)) → save 호출 %d번""".formatted(bad.saveCalls, good.saveCalls));
    }
}
