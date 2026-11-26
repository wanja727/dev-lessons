package lab.devlessons.equalshashcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "equals와 hashCode"의 "실무에서는" 구역을 확인한다.
 * - 흔한 실수: equals의 인자를 Member로 받아 재정의가 아니라 오버로딩이 된다
 * - @Override를 붙이면 컴파일러가 그 실수를 잡는다
 * - 값 객체를 record로 만들면 equals와 hashCode를 함께 얻는다
 */
class EqualsHashCodePracticeTest {

    private static final String LESSON = "equals-hashcode";

    /** 흔한 실수: equals(Member). Object의 equals를 재정의하지 않고 새 메서드를 하나 더 만든다 */
    static class OverloadMember {
        final String email;

        OverloadMember(String email) {
            this.email = email;
        }

        public boolean equals(OverloadMember m) {
            return email.equals(m.email);
        }

        @Override
        public int hashCode() {
            return email.hashCode();
        }
    }

    record Email(String value) {
    }

    @Test
    @DisplayName("equals(Member)는 오버로딩이라 a.equals(b)는 true여도 list.contains(b)와 set.contains(b)는 false다")
    void overloadedEqualsIsNotUsedByCollections() {
        OverloadMember a = new OverloadMember("kim@a.com");
        OverloadMember b = new OverloadMember("kim@a.com");
        List<OverloadMember> list = new ArrayList<>();
        list.add(a);
        Set<OverloadMember> set = new HashSet<>();
        set.add(a);
        Object asObject = b;

        assertThat(a.equals(b)).isTrue();
        assertThat(a.equals(asObject)).isFalse();
        assertThat(list.contains(b)).isFalse();
        assertThat(set.contains(b)).isFalse();
        CiOutput.record(LESSON, "overload", "[equals(Member m)로 쓴 Member, hashCode는 이메일]\n"
                + "a.equals(b)          → " + a.equals(b) + "\n"
                + "a.equals((Object) b) → " + a.equals(asObject) + "\n"
                + "list.contains(b)     → " + list.contains(b) + "\n"
                + "set.contains(b)      → " + set.contains(b));
    }

    @Test
    @DisplayName("equals(Member)에 @Override를 붙이면 컴파일 오류가 나고, equals(Object)로 고치면 컴파일된다")
    void overrideAnnotationCatchesOverload() {
        String wrong = """
                public class Member {
                    String email;
                    @Override
                    public boolean equals(Member m) {
                        return email.equals(m.email);
                    }
                }
                """;
        String fixed = """
                public class Member {
                    String email;
                    @Override
                    public boolean equals(Object o) {
                        return o instanceof Member m
                                && email.equals(m.email);
                    }
                    @Override
                    public int hashCode() {
                        return email.hashCode();
                    }
                }
                """;
        JavaSource.Result bad = JavaSource.compile("Member", wrong);
        JavaSource.Result good = JavaSource.compile("Member", fixed);

        assertThat(bad.ok()).isFalse();
        assertThat(bad.errors()).anySatisfy(e -> assertThat(e).contains("does not override"));
        assertThat(good.ok()).isTrue();
        CiOutput.record(LESSON, "override", "equals(Member m)에 @Override → " + bad.errors().get(0) + "\n"
                + "equals(Object o)로 고친 뒤 → 컴파일 " + (good.ok() ? "성공" : "실패"));
    }

    @Test
    @DisplayName("record Email은 값이 같으면 equals가 true이고 HashSet에 한 번만 들어간다")
    void recordValueObject() {
        Set<Email> set = new HashSet<>();
        boolean first = set.add(new Email("kim@a.com"));
        boolean second = set.add(new Email("kim@a.com"));

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(set).hasSize(1);
        CiOutput.record(LESSON, "record", "record Email(String value) {}\n"
                + "set.add(new Email(\"kim@a.com\")) → " + first + "\n"
                + "set.add(new Email(\"kim@a.com\")) → " + second + "\n"
                + "set.size()                       → " + set.size());
    }
}
