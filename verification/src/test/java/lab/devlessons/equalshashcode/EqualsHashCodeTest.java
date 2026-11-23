package lab.devlessons.equalshashcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "equals와 hashCode"의 주장을 검증한다. 순수 JUnit 테스트.
 * 레슨의 Member 한 클래스를 재정의한 것에 따라 다섯 클래스로 나눠 둔다.
 * equals는 불릴 때마다 CALLS에 한 줄을 남긴다. 호출 횟수는 그 줄 수로 센다.
 */
class EqualsHashCodeTest {

    private static final String LESSON = "equals-hashcode";

    /** 넣는 회원 다섯 명과, 들어 있지 않은 회원 */
    private static final List<String> FIVE =
            List.of("kim@a.com", "lee@a.com", "park@a.com", "choi@a.com", "jung@a.com");
    private static final String MISSING = "han@a.com";

    /** equals가 불린 기록. "부른 쪽.equals(인자)" */
    private static final List<String> CALLS = new ArrayList<>();

    /** 코드 plain: 아무것도 재정의하지 않는다 */
    static class PlainMember {
        String email;

        PlainMember(String email) {
            this.email = email;
        }
    }

    /** 코드 equalsOnly: equals만 재정의한다 */
    static class EqualsOnlyMember {
        String email;

        EqualsOnlyMember(String email) {
            this.email = email;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EqualsOnlyMember m)) {
                return false;
            }
            CALLS.add(email + ".equals(" + m.email + ")");
            return email.equals(m.email);
        }
    }

    /** 코드 both: equals와 hashCode를 둘 다 재정의한다 */
    static class BothMember {
        String email;

        BothMember(String email) {
            this.email = email;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BothMember m)) {
                return false;
            }
            CALLS.add(email + ".equals(" + m.email + ")");
            return email.equals(m.email);
        }

        @Override
        public int hashCode() {
            return email.hashCode();
        }
    }

    /** hashCode만 재정의한다 */
    static class HashCodeOnlyMember {
        String email;

        HashCodeOnlyMember(String email) {
            this.email = email;
        }

        @Override
        public int hashCode() {
            return email.hashCode();
        }
    }

    /** 코드 constant: hashCode가 항상 1이다 */
    static class ConstantMember {
        String email;

        ConstantMember(String email) {
            this.email = email;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConstantMember m)) {
                return false;
            }
            CALLS.add(email + ".equals(" + m.email + ")");
            return email.equals(m.email);
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    /** 재정의하지 않은 것과 똑같이 판정하고, 불린 것만 기록한다 */
    static class CountingPlainMember {
        String email;

        CountingPlainMember(String email) {
            this.email = email;
        }

        @Override
        public boolean equals(Object o) {
            CALLS.add(email + ".equals(...)");
            return super.equals(o);
        }
    }

    /** equals는 email만 비교하는데 hashCode에는 가입 시각까지 넣었다. 규약을 어긴다 */
    static class ExtraFieldMember {
        String email;
        long joinedAt;

        ExtraFieldMember(String email, long joinedAt) {
            this.email = email;
            this.joinedAt = joinedAt;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ExtraFieldMember m)) {
                return false;
            }
            return email.equals(m.email);
        }

        @Override
        public int hashCode() {
            return Objects.hash(email, joinedAt);
        }
    }

    record RecordMember(String email) {
    }

    @BeforeEach
    void clearCalls() {
        CALLS.clear();
    }

    @Test
    @DisplayName("재정의하지 않으면 이메일이 같아도 a.equals(b)는 false, a.equals(a)는 true, list.contains(b)는 false다")
    void plainUsesIdentity() {
        PlainMember a = new PlainMember("kim@a.com");
        PlainMember b = new PlainMember("kim@a.com");
        List<PlainMember> list = new ArrayList<>();
        list.add(a);

        assertThat(a.equals(b)).isFalse();
        assertThat(a.equals(a)).isTrue();
        assertThat(list.contains(b)).isFalse();

        CiOutput.record(LESSON, "plain", "[재정의하지 않은 Member]\n"
                + "a == b            → " + (a == b) + "\n"
                + "a.equals(b)       → " + a.equals(b) + "\n"
                + "a.equals(a)       → " + a.equals(a) + "\n"
                + "list.contains(b)  → " + list.contains(b));
    }

    @Test
    @DisplayName("재정의하지 않으면 hashCode가 다른 두 객체는 HashSet과 HashMap에서도 찾지 못하고 equals는 0번 불린다")
    void plainIsNotFoundInHashSet() {
        CountingPlainMember a = new CountingPlainMember("kim@a.com");
        CountingPlainMember b = new CountingPlainMember("kim@a.com");
        while (b.hashCode() == a.hashCode()) {
            b = new CountingPlainMember("kim@a.com");
        }
        List<CountingPlainMember> list = new ArrayList<>();
        list.add(a);
        Set<CountingPlainMember> set = new HashSet<>();
        set.add(a);
        Map<CountingPlainMember, String> map = new HashMap<>();
        map.put(a, "VIP");

        assertThat(list.contains(b)).isFalse();
        CALLS.clear();
        assertThat(set.contains(b)).isFalse();
        assertThat(CALLS).isEmpty();
        assertThat(map.get(b)).isNull();
        assertThat(set.add(b)).isTrue();
        assertThat(set).hasSize(2);
    }

    @Test
    @DisplayName("equals를 재정의하면 a == b는 false 그대로이고 a.equals(b)와 list.contains(b)는 true다")
    void equalsOverrideComparesEmail() {
        EqualsOnlyMember a = new EqualsOnlyMember("kim@a.com");
        EqualsOnlyMember b = new EqualsOnlyMember("kim@a.com");
        List<EqualsOnlyMember> list = new ArrayList<>();
        list.add(a);

        assertThat(a == b).isFalse();
        assertThat(a.equals(b)).isTrue();
        assertThat(list.contains(b)).isTrue();
        assertThat(a.equals("kim@a.com")).isFalse();
    }

    @Test
    @DisplayName("equals만 재정의하면 hashCode가 다른 두 객체는 HashSet과 HashMap에서 서로 찾지 못하고 equals는 0번 불린다")
    void equalsOnlyNotFoundInSet() {
        EqualsOnlyMember a = new EqualsOnlyMember("kim@a.com");
        // Object의 hashCode는 다른 객체에 같은 값을 줄 수도 있다. 다른 값이 나올 때까지 b를 다시 만든다
        EqualsOnlyMember b = new EqualsOnlyMember("kim@a.com");
        int tries = 1;
        while (b.hashCode() == a.hashCode()) {
            b = new EqualsOnlyMember("kim@a.com");
            tries++;
        }
        List<EqualsOnlyMember> list = new ArrayList<>();
        list.add(a);
        Set<EqualsOnlyMember> set = new HashSet<>();
        set.add(a);
        Map<EqualsOnlyMember, String> map = new HashMap<>();
        map.put(a, "VIP");

        boolean inList = list.contains(b);
        CALLS.clear();
        boolean inSet = set.contains(b);
        int callsInSet = CALLS.size();

        assertThat(inList).isTrue();
        assertThat(inSet).isFalse();
        assertThat(callsInSet).isZero();
        assertThat(map.get(b)).isNull();
        assertThat(set.add(b)).isTrue();
        assertThat(set).hasSize(2);

        CiOutput.record(LESSON, "equalsOnly", "[equals만 재정의한 Member]\n"
                + "a.hashCode()      → " + a.hashCode() + "\n"
                + "b.hashCode()      → " + b.hashCode() + "  (" + tries + "번째에 만든 b)\n"
                + "list.contains(b)  → " + inList + "\n"
                + "set.contains(b)   → " + inSet + "  (equals 호출 " + callsInSet + "번)\n"
                + "set.add(b)        → true\n"
                + "set.size()        → " + set.size() + "\n"
                + "map.get(b)        → " + map.get(b));
    }

    @Test
    @DisplayName("둘 다 재정의하면 HashSet과 HashMap이 같은 회원으로 보고, contains 동안 equals는 1번 불린다")
    void bothFoundInHashSet() {
        BothMember a = new BothMember("kim@a.com");
        BothMember b = new BothMember("kim@a.com");
        List<BothMember> list = new ArrayList<>();
        list.add(a);
        Set<BothMember> set = new HashSet<>();
        set.add(a);
        Map<BothMember, String> map = new HashMap<>();
        map.put(a, "VIP");

        boolean inList = list.contains(b);
        CALLS.clear();
        boolean inSet = set.contains(b);
        int callsInSet = CALLS.size();
        String grade = map.get(b);
        boolean added = set.add(b);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(inList).isTrue();
        assertThat(inSet).isTrue();
        assertThat(callsInSet).isEqualTo(1);
        assertThat(added).isFalse();
        assertThat(set).hasSize(1);
        assertThat(grade).isEqualTo("VIP");

        map.put(b, "GOLD");
        assertThat(map).hasSize(1);
        assertThat(map.get(a)).isEqualTo("GOLD");

        CiOutput.record(LESSON, "both", "[equals와 hashCode를 둘 다 재정의한 Member]\n"
                + "a.hashCode()      → " + a.hashCode() + "\n"
                + "b.hashCode()      → " + b.hashCode() + "\n"
                + "list.contains(b)  → " + inList + "\n"
                + "set.contains(b)   → " + inSet + "  (equals 호출 " + callsInSet + "번)\n"
                + "set.add(b)        → " + added + "\n"
                + "set.size()        → " + set.size() + "\n"
                + "map.get(b)        → " + grade + "\n"
                + "map.put(b, \"GOLD\") 뒤 map.size() → " + map.size() + ", map.get(a) → " + map.get(a));
    }

    @Test
    @DisplayName("hashCode만 재정의하면 hashCode가 같아도 equals가 false라서 찾지 못한다")
    void hashCodeOnlyIsNotFound() {
        HashCodeOnlyMember a = new HashCodeOnlyMember("kim@a.com");
        HashCodeOnlyMember b = new HashCodeOnlyMember("kim@a.com");
        Set<HashCodeOnlyMember> set = new HashSet<>();
        set.add(a);

        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(set.contains(b)).isFalse();
        set.add(b);
        assertThat(set).hasSize(2);
    }

    @Test
    @DisplayName("\"Aa\"와 \"BB\"는 hashCode가 둘 다 2112이고 equals는 false다. 둘 다 HashSet에 들어가고 둘 다 찾는다")
    void sameHashCodeDifferentEquals() {
        assertThat("Aa".hashCode()).isEqualTo(2112);
        assertThat("BB".hashCode()).isEqualTo(2112);
        assertThat("Aa".equals("BB")).isFalse();

        Set<BothMember> set = new HashSet<>();
        set.add(new BothMember("Aa"));
        set.add(new BothMember("BB"));

        assertThat(set).hasSize(2);
        assertThat(set.contains(new BothMember("Aa"))).isTrue();
        assertThat(set.contains(new BothMember("BB"))).isTrue();

        CiOutput.record(LESSON, "collision", "\"Aa\".hashCode()    → " + "Aa".hashCode() + "\n"
                + "\"BB\".hashCode()    → " + "BB".hashCode() + "\n"
                + "\"Aa\".equals(\"BB\")  → " + "Aa".equals("BB") + "\n"
                + "두 회원을 넣은 뒤 set.size() → " + set.size() + "\n"
                + "set.contains(new Member(\"Aa\")) → " + set.contains(new BothMember("Aa")) + "\n"
                + "set.contains(new Member(\"BB\")) → " + set.contains(new BothMember("BB")));
    }

    @Test
    @DisplayName("이메일 여섯 개의 hashCode는 모두 다르다")
    void sixEmailsHaveDistinctHashCodes() {
        Set<Integer> hashCodes = new HashSet<>();
        for (String email : FIVE) {
            hashCodes.add(email.hashCode());
        }
        hashCodes.add(MISSING.hashCode());

        assertThat(hashCodes).hasSize(6);
    }

    @Test
    @DisplayName("회원 5명을 넣는 동안 equals는 email.hashCode()에서 0번, 항상 1에서 10번 불린다")
    void equalsCallsWhileAdding() {
        Set<Object> both = new HashSet<>();
        int bothCalls = addFive(both, BothMember::new);
        Set<Object> constant = new HashSet<>();
        int constantCalls = addFive(constant, ConstantMember::new);

        assertThat(both).hasSize(5);
        assertThat(constant).hasSize(5);
        assertThat(bothCalls).isZero();
        assertThat(constantCalls).isEqualTo(10);
    }

    @Test
    @DisplayName("들어 있지 않은 회원을 찾는 동안 equals는 email.hashCode()에서 0번, 항상 1에서 5번 불린다. 결과는 둘 다 false다")
    void equalsCallsWhileSearchingMissing() {
        Set<Object> both = new HashSet<>();
        int bothAdding = addFive(both, BothMember::new);
        CALLS.clear();
        boolean bothFound = both.contains(new BothMember(MISSING));
        List<String> bothCalls = List.copyOf(CALLS);

        Set<Object> constant = new HashSet<>();
        int constantAdding = addFive(constant, ConstantMember::new);
        CALLS.clear();
        boolean constantFound = constant.contains(new ConstantMember(MISSING));
        List<String> constantCalls = List.copyOf(CALLS);

        assertThat(bothFound).isFalse();
        assertThat(constantFound).isFalse();
        assertThat(bothCalls).isEmpty();
        assertThat(constantCalls).containsExactly(
                "han@a.com.equals(kim@a.com)",
                "han@a.com.equals(lee@a.com)",
                "han@a.com.equals(park@a.com)",
                "han@a.com.equals(choi@a.com)",
                "han@a.com.equals(jung@a.com)");

        CiOutput.record(LESSON, "count", "[hashCode가 email.hashCode()일 때]\n"
                + "회원 5명을 넣는 동안 equals 호출 → " + bothAdding + "번\n"
                + "set.contains(han@a.com) → " + bothFound + ", equals 호출 " + bothCalls.size() + "번\n"
                + "\n[hashCode가 항상 1일 때]\n"
                + "회원 5명을 넣는 동안 equals 호출 → " + constantAdding + "번\n"
                + "set.contains(han@a.com) → " + constantFound + ", equals 호출 " + constantCalls.size() + "번\n"
                + String.join("\n", constantCalls));
    }

    @Test
    @DisplayName("hashCode가 항상 1이어도 결과는 맞다: 같은 이메일을 다시 넣으면 false가 돌아오고 크기는 그대로다")
    void constantHashCodeStillCorrect() {
        Set<Object> set = new HashSet<>();
        addFive(set, ConstantMember::new);

        assertThat(set.add(new ConstantMember("kim@a.com"))).isFalse();
        assertThat(set).hasSize(5);
        assertThat(set.contains(new ConstantMember("jung@a.com"))).isTrue();

        ConstantMember a = new ConstantMember("kim@a.com");
        ConstantMember b = new ConstantMember("kim@a.com");
        Set<ConstantMember> two = new HashSet<>();
        two.add(a);
        two.add(b);
        Map<ConstantMember, String> map = new HashMap<>();
        map.put(a, "VIP");

        assertThat(two).hasSize(1);
        assertThat(two.contains(b)).isTrue();
        assertThat(map.get(b)).isEqualTo("VIP");
    }

    @Test
    @DisplayName("키로 넣은 뒤 이메일을 바꾸면 그 객체로도, 새 객체로도 찾지 못한다. 크기는 1 그대로다")
    void mutatedKeyIsLost() {
        assertThat("kim@a.com".hashCode()).isNotEqualTo("lee@a.com".hashCode());

        BothMember a = new BothMember("kim@a.com");
        Map<BothMember, String> map = new HashMap<>();
        map.put(a, "VIP");
        int before = a.hashCode();
        String untouched = map.get(a);

        a.email = "lee@a.com";

        String byItself = map.get(a);
        String byOldEmail = map.get(new BothMember("kim@a.com"));
        String byNewEmail = map.get(new BothMember("lee@a.com"));

        assertThat(untouched).isEqualTo("VIP");
        assertThat(byItself).isNull();
        assertThat(map).hasSize(1);
        assertThat(byOldEmail).isNull();
        assertThat(byNewEmail).isNull();
        assertThat(map.containsValue("VIP")).isTrue();

        CiOutput.record(LESSON, "mutate", "넣을 때 a.hashCode()  → " + before + "\n"
                + "바꾼 뒤 a.hashCode()  → " + a.hashCode() + "\n"
                + "map.get(a)                         → " + byItself + "\n"
                + "map.size()                         → " + map.size() + "\n"
                + "map.get(new Member(\"kim@a.com\"))   → " + byOldEmail + "\n"
                + "map.get(new Member(\"lee@a.com\"))   → " + byNewEmail);
    }

    @Test
    @DisplayName("HashSet에 넣은 뒤 이메일을 바꾸면 contains가 false다")
    void mutatedElementIsLostInHashSet() {
        BothMember a = new BothMember("kim@a.com");
        Set<BothMember> set = new HashSet<>();
        set.add(a);

        a.email = "lee@a.com";

        assertThat(set.contains(a)).isFalse();
        assertThat(set).hasSize(1);
    }

    @Test
    @DisplayName("email이 final이면 a.email = \"lee@a.com\"은 컴파일 오류다")
    void finalFieldCannotBeAssigned(@TempDir Path out) {
        List<Diagnostic<? extends JavaFileObject>> withFinal = compile(source("final String email;"), out);
        List<Diagnostic<? extends JavaFileObject>> withoutFinal = compile(source("String email;"), out);

        assertThat(withoutFinal).isEmpty();
        assertThat(withFinal).hasSize(1);
        Diagnostic<? extends JavaFileObject> error = withFinal.get(0);
        String line = source("final String email;").split("\n")[(int) error.getLineNumber() - 1].trim();
        assertThat(error.getKind()).isEqualTo(Diagnostic.Kind.ERROR);
        assertThat(line).isEqualTo("a.email = \"lee@a.com\";");
        assertThat(error.getMessage(Locale.ENGLISH)).contains("cannot assign a value to final variable email");

        CiOutput.record(LESSON, "final", "오류가 난 줄: " + line + "\n"
                + "error: " + error.getMessage(Locale.ENGLISH));
    }

    @Test
    @DisplayName("record는 구성 요소로 equals와 hashCode를 함께 만들어 준다")
    void recordGeneratesBoth() {
        RecordMember a = new RecordMember("kim@a.com");
        RecordMember b = new RecordMember("kim@a.com");
        Set<RecordMember> set = new HashSet<>();
        set.add(a);

        assertThat(a == b).isFalse();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(set.contains(b)).isTrue();
    }

    @Test
    @DisplayName("Objects.hash는 넣은 값이 같으면 같은 값을 돌려준다")
    void objectsHashIsSameForSameValues() {
        assertThat(Objects.hash("kim@a.com", "kim")).isEqualTo(Objects.hash("kim@a.com", "kim"));
    }

    @Test
    @DisplayName("equals가 비교하지 않는 필드를 hashCode에 넣으면 equals는 true인데 hashCode가 달라 찾지 못한다")
    void extraFieldInHashCodeBreaksContract() {
        ExtraFieldMember a = new ExtraFieldMember("kim@a.com", 1L);
        ExtraFieldMember b = new ExtraFieldMember("kim@a.com", 2L);
        Set<ExtraFieldMember> set = new HashSet<>();
        set.add(a);

        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode());
        assertThat(set.contains(b)).isFalse();
    }

    @Test
    @DisplayName("재정의하지 않은 hashCode가 같은 두 객체가 실제로 나온다. 그 둘이라면 equals만 재정의해도 찾는다")
    void identityHashCodesCanCollide() {
        Map<Integer, EqualsOnlyMember> seen = new HashMap<>();
        EqualsOnlyMember first = null;
        EqualsOnlyMember second = null;
        int made = 0;
        while (first == null && made < 5_000_000) {
            EqualsOnlyMember m = new EqualsOnlyMember("kim@a.com");
            made++;
            EqualsOnlyMember earlier = seen.put(m.hashCode(), m);
            if (earlier != null) {
                first = earlier;
                second = m;
            }
        }

        assertThat(first).as("객체 500만 개 안에 hashCode가 같은 둘이 나온다").isNotNull();
        assertThat(first == second).isFalse();
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        Set<EqualsOnlyMember> set = new HashSet<>();
        set.add(first);
        assertThat(set.contains(second)).isTrue();

        CiOutput.record(LESSON, "identity", "객체를 " + made + "개 만들었을 때 hashCode가 같은 둘이 나왔어요\n"
                + "first.hashCode()   → " + first.hashCode() + "\n"
                + "second.hashCode()  → " + second.hashCode() + "\n"
                + "first == second    → " + (first == second) + "\n"
                + "first만 넣은 set.contains(second) → " + set.contains(second));
    }

    /** 회원 다섯 명을 넣고, 넣는 동안 equals가 불린 횟수를 돌려준다 */
    private static int addFive(Set<Object> set, Function<String, Object> create) {
        CALLS.clear();
        for (String email : FIVE) {
            set.add(create.apply(email));
        }
        return CALLS.size();
    }

    /** 코드 both의 Member와 코드 mutate. field 자리에 email 필드의 선언을 넣는다 */
    private static String source(String field) {
        return """
                import java.util.HashMap;
                import java.util.Map;

                class Member {
                    %s
                    Member(String email) {
                        this.email = email;
                    }
                    @Override
                    public boolean equals(Object o) {
                        if (!(o instanceof Member m)) {
                            return false;
                        }
                        return email.equals(m.email);
                    }
                    @Override
                    public int hashCode() {
                        return email.hashCode();
                    }
                }

                class Mutate {
                    static void run() {
                        Member a = new Member("kim@a.com");
                        Map<Member, String> map = new HashMap<>();
                        map.put(a, "VIP");

                        a.email = "lee@a.com";

                        System.out.println(map.get(a));
                        System.out.println(map.size());
                    }
                }
                """.formatted(field);
    }

    /** 소스를 컴파일하고 컴파일러가 낸 진단을 돌려준다 */
    private static List<Diagnostic<? extends JavaFileObject>> compile(String source, Path out) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK로 돌려야 한다 (JRE에는 컴파일러가 없다)").isNotNull();
        JavaFileObject file = new SimpleJavaFileObject(URI.create("string:///Member.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        compiler.getTask(null, null, diagnostics, List.of("-d", out.toString(), "-proc:none"), null, List.of(file)).call();
        return diagnostics.getDiagnostics();
    }
}
