package lab.devlessons.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * DB 과정의 검증 테스트가 SQL을 직접 실행할 때 쓰는 작은 도구.
 * 결과는 레슨에 그대로 붙일 수 있게 "열1 | 열2" 모양의 줄로 돌려준다.
 */
public final class Jdbc {

    private Jdbc() {
    }

    /** 문장 하나를 자동 커밋으로 실행한다 */
    public static void exec(DataSource ds, String... sqls) {
        try (Connection c = ds.getConnection()) {
            exec(c, sqls);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void exec(Connection c, String... sqls) throws SQLException {
        try (Statement s = c.createStatement()) {
            for (String sql : sqls) {
                s.execute(sql);
            }
        }
    }

    /** UPDATE, DELETE가 바꾼 행 수 */
    public static int update(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    public static List<String> rows(DataSource ds, String sql) {
        try (Connection c = ds.getConnection()) {
            return rows(c, sql);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 결과의 행마다 열을 " | "로 이은 줄. NULL은 NULL로 적는다 */
    public static List<String> rows(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                List<String> cols = new ArrayList<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    cols.add(v == null ? "NULL" : v.toString());
                }
                out.add(String.join(" | ", cols));
            }
        }
        return out;
    }

    public static long number(DataSource ds, String sql) {
        List<String> r = rows(ds, sql);
        return Long.parseLong(r.get(0));
    }

    public static long number(Connection c, String sql) throws SQLException {
        return Long.parseLong(rows(c, sql).get(0));
    }

    /** 실행하다 난 SQLException의 SQLState. 오류가 없으면 "ok" */
    public static String sqlState(DataSource ds, String sql) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
            return "ok";
        } catch (SQLException e) {
            return e.getSQLState();
        }
    }

    /** 실행하다 난 오류의 "SQLState 메시지 첫 줄" */
    public static String error(DataSource ds, String sql) {
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            s.execute(sql);
            return "ok";
        } catch (SQLException e) {
            return e.getSQLState() + " " + e.getMessage().lines().findFirst().orElse("");
        }
    }

    /** EXPLAIN 결과를 줄로 이어 돌려준다 */
    public static String plan(Connection c, String explain) throws SQLException {
        return String.join("\n", rows(c, explain));
    }

    public static String plan(DataSource ds, String explain) {
        return String.join("\n", rows(ds, explain));
    }
}
