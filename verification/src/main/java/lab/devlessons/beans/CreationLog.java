package lab.devlessons.beans;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 객체가 만들어진 순서를 기록한다 (컨테이너가 의존 객체를 먼저 만드는지 확인용) */
public final class CreationLog {

    private static final List<String> LOG = new CopyOnWriteArrayList<>();

    private CreationLog() {
    }

    public static void add(String line) {
        LOG.add(line);
    }

    public static List<String> lines() {
        return List.copyOf(LOG);
    }
}
