package lab.devlessons.support;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 설계 레슨이 "요구가 오면 어느 파일을 고치나"를 말할 때 쓴다.
 * 요구를 반영하기 전과 후의 소스(키는 파일 이름, 값은 소스)를 비교해 고친 파일, 새로 만든 파일, 지운 파일, 그대로인 파일을 나눈다.
 * 줄 끝의 빈칸은 비교하지 않는다.
 *
 * <pre>
 * SourceChange c = SourceChange.between(before, after);
 * c.changed();  // [Checkout]
 * c.added();    // [KakaoPayment]
 * </pre>
 */
public record SourceChange(List<String> changed, List<String> added, List<String> removed, List<String> same) {

    public static SourceChange between(Map<String, String> before, Map<String, String> after) {
        TreeSet<String> changed = new TreeSet<>();
        TreeSet<String> added = new TreeSet<>();
        TreeSet<String> removed = new TreeSet<>();
        TreeSet<String> same = new TreeSet<>();
        for (String name : before.keySet()) {
            if (!after.containsKey(name)) {
                removed.add(name);
            } else if (normalize(before.get(name)).equals(normalize(after.get(name)))) {
                same.add(name);
            } else {
                changed.add(name);
            }
        }
        for (String name : after.keySet()) {
            if (!before.containsKey(name)) {
                added.add(name);
            }
        }
        return new SourceChange(List.copyOf(changed), List.copyOf(added), List.copyOf(removed), List.copyOf(same));
    }

    /** 레슨에 붙일 출력 모양 */
    public String describe() {
        return "고친 파일      → " + join(changed) + "\n"
                + "새로 만든 파일 → " + join(added) + "\n"
                + "그대로인 파일  → " + join(same);
    }

    private static String join(List<String> names) {
        return names.isEmpty() ? "없음" : String.join(", ", names);
    }

    private static String normalize(String src) {
        return src.strip().replaceAll("[ \\t]+\\n", "\n");
    }
}
