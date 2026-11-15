package lab.devlessons.osiv;

/** 두 테스트(OSIV 켜짐/꺼짐)가 같은 데이터를 만든다 */
final class OsivFixture {

    private OsivFixture() {
    }

    static Long create(OsivTeamRepository teams, OsivPlayerRepository players) {
        players.deleteAll();
        teams.deleteAll();
        OsivTeam team = teams.save(new OsivTeam("레드"));
        return players.save(new OsivPlayer("kim", team)).getId();
    }
}
