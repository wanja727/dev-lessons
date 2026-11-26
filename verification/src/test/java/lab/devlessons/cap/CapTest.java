package lab.devlessons.cap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 레슨 "CAP 정리와 결과적 일관성"의 주장을 작은 복제 저장소 모형으로 검증한다.
 * 복제본 3개(A, B, C)가 키 하나를 들고, 네트워크 분할은 "서로 메시지를 못 보내는 쪽"으로 흉내 낸다.
 * 실제 DB가 아니라 원리를 보이는 모형이다. 레슨 본문에 그렇게 밝힌다.
 */
class CapTest {

    static final String LESSON = "cap";

    record Versioned(String value, long version) {
    }

    /** 복제본 3개와 분할. reachable(a, b)가 false면 a와 b는 서로 못 닿는다 */
    static class Cluster {
        final Map<String, Versioned> replicas = new HashMap<>();
        Set<String> sideOne = Set.of("A", "B", "C");
        long clock = 0;

        Cluster() {
            for (String r : List.of("A", "B", "C")) {
                replicas.put(r, new Versioned("v0", 0));
            }
        }

        void partition(Set<String> side) {
            sideOne = side;
        }

        void heal() {
            sideOne = Set.of("A", "B", "C");
        }

        boolean reachable(String a, String b) {
            return sideOne.contains(a) == sideOne.contains(b);
        }

        List<String> reachableFrom(String node) {
            List<String> out = new ArrayList<>();
            for (String r : List.of("A", "B", "C")) {
                if (reachable(node, r)) {
                    out.add(r);
                }
            }
            return out;
        }

        /** 정족수 쓰기: w개 이상에 써야 성공. 못 닿으면 실패(가용성 포기) */
        boolean quorumWrite(String via, String value, int w) {
            List<String> targets = reachableFrom(via);
            if (targets.size() < w) {
                return false;
            }
            long v = ++clock;
            for (String t : targets) {
                replicas.put(t, new Versioned(value, v));
            }
            return true;
        }

        /** 정족수 읽기: r개 이상에서 읽어 버전이 가장 큰 값. 못 닿으면 실패 */
        String quorumRead(String via, int r) {
            List<String> targets = reachableFrom(via);
            if (targets.size() < r) {
                return null;
            }
            return targets.stream().map(replicas::get)
                    .max((x, y) -> Long.compare(x.version(), y.version())).orElseThrow().value();
        }

        /** 가용성 우선 쓰기: 닿는 쪽에만 쓰고 늘 성공 */
        void localWrite(String via, String value) {
            long v = ++clock;
            for (String t : reachableFrom(via)) {
                replicas.put(t, new Versioned(value, v));
            }
        }

        String localRead(String node) {
            return replicas.get(node).value();
        }

        /** 분할이 풀린 뒤 서로 맞추기: 버전이 큰 값이 이긴다(마지막 쓰기 승리) */
        void antiEntropy() {
            Versioned newest = replicas.values().stream()
                    .max((x, y) -> Long.compare(x.version(), y.version())).orElseThrow();
            for (String r : List.of("A", "B", "C")) {
                replicas.put(r, newest);
            }
        }
    }

    @Test
    @DisplayName("분할이 없으면 정족수(W=2, R=2)로 쓰고 읽어 늘 최신 값을 읽는다")
    void noPartition() {
        Cluster c = new Cluster();
        boolean ok = c.quorumWrite("A", "닉네임=준호2", 2);
        String read = c.quorumRead("C", 2);

        assertThat(ok).isTrue();
        assertThat(read).isEqualTo("닉네임=준호2");
        CiOutput.record(LESSON, "normal", "[분할 없음, 복제본 3개, W=2, R=2]\n"
                + "A에서 쓰기 → " + (ok ? "성공" : "실패") + "\nC에서 읽기 → " + read);
    }

    @Test
    @DisplayName("분할 중 일관성을 고르면(정족수) 소수 쪽(C)은 쓰기와 읽기를 거절하고, 다수 쪽(A, B)은 계속 일한다")
    void consistencyChoice() {
        Cluster c = new Cluster();
        c.partition(Set.of("A", "B")); // C가 떨어져 나감
        boolean majorityWrite = c.quorumWrite("A", "준호2", 2);
        boolean minorityWrite = c.quorumWrite("C", "준호3", 2);
        String minorityRead = c.quorumRead("C", 2);
        String majorityRead = c.quorumRead("B", 2);

        assertThat(majorityWrite).isTrue();
        assertThat(minorityWrite).isFalse();
        assertThat(minorityRead).isNull();
        assertThat(majorityRead).isEqualTo("준호2");
        CiOutput.record(LESSON, "cp", "[분할: {A, B} | {C}, 정족수 W=2, R=2]\n"
                + "A에서 쓰기 → " + (majorityWrite ? "성공" : "거절") + "\n"
                + "C에서 쓰기 → " + (minorityWrite ? "성공" : "거절 (2개에 닿지 못함)") + "\n"
                + "C에서 읽기 → " + (minorityRead == null ? "거절" : minorityRead) + "\n"
                + "B에서 읽기 → " + majorityRead);
    }

    @Test
    @DisplayName("분할 중 가용성을 고르면 양쪽 모두 응답하지만 서로 다른 값을 보고, 분할이 풀리면 맞춰지며 한쪽 쓰기를 잃는다")
    void availabilityChoice() {
        Cluster c = new Cluster();
        c.partition(Set.of("A", "B"));
        c.localWrite("A", "준호2");
        c.localWrite("C", "준호3");
        String atA = c.localRead("A");
        String atC = c.localRead("C");

        c.heal();
        c.antiEntropy();
        String after = c.localRead("A");
        boolean converged = after.equals(c.localRead("B")) && after.equals(c.localRead("C"));

        assertThat(atA).isNotEqualTo(atC);
        assertThat(converged).isTrue();
        assertThat(after).isEqualTo("준호3");
        CiOutput.record(LESSON, "ap", "[분할: {A, B} | {C}, 닿는 쪽에만 쓰고 늘 응답]\n"
                + "A에 준호2, C에 준호3을 씀 (둘 다 성공)\n"
                + "분할 중 A에서 읽기 → " + atA + ", C에서 읽기 → " + atC + " (서로 다름)\n"
                + "분할이 풀리고 맞춘 뒤 → A, B, C 모두 " + after + " (마지막 쓰기 승리, 준호2는 사라짐)");
    }

    @Test
    @DisplayName("정족수가 R + W <= N이면 분할이 없어도 옛 값을 읽을 수 있고, R + W > N이면 최신 값과 겹친다")
    void quorumOverlap() {
        Cluster c = new Cluster();
        // W=1: A에만 쓰게 B, C를 잠시 떼어 놓고 쓴 뒤 붙인다(복제가 아직 안 된 상황)
        c.partition(Set.of("A"));
        c.quorumWrite("A", "준호2", 1);
        c.heal();
        String readR1FromC = c.replicas.get("C").value(); // R=1, C 하나만 읽음
        String readR3 = c.quorumRead("C", 3);            // R=3, 셋 다 읽음

        assertThat(readR1FromC).isEqualTo("v0");
        assertThat(readR3).isEqualTo("준호2");
        CiOutput.record(LESSON, "quorum", "[N=3, A에만 준호2를 씀(W=1), 아직 복제 전]\n"
                + "R=1로 C만 읽기 → " + readR1FromC + " (R + W = 2 <= 3, 옛 값)\n"
                + "R=3으로 읽기 → " + readR3 + " (R + W = 4 > 3, 최신 값과 겹침)");
    }
}
