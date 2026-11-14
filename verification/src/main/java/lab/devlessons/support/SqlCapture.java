package lab.devlessons.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Hibernate가 DB로 보내는 모든 SQL을 모아 두고 테스트에서 개수와 내용을 확인한다. */
public class SqlCapture implements StatementInspector {

    private static final List<String> SQLS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        SQLS.add(sql);
        return sql;
    }

    public static void clear() {
        SQLS.clear();
    }

    public static int count() {
        return SQLS.size();
    }

    public static List<String> sqls() {
        return List.copyOf(SQLS);
    }
}
