package lab.devlessons.audit;

/** 요청을 보낸 사람. 실제로는 Spring Security의 SecurityContext에서 꺼낸다 */
public final class CurrentActor {

    private static final ThreadLocal<String> ACTOR = ThreadLocal.withInitial(() -> "system");

    private CurrentActor() {
    }

    public static void set(String actor) {
        ACTOR.set(actor);
    }

    public static String get() {
        return ACTOR.get();
    }

    public static void clear() {
        ACTOR.remove();
    }
}
