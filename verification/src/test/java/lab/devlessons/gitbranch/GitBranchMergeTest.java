package lab.devlessons.gitbranch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.JavaSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 레슨 "Git 브랜치와 병합"(T1). 임시 폴더에 저장소를 만들고 git 명령을 실제로 돌려 결과를 본다.
 */
class GitBranchMergeTest {

    static final String LESSON = "git-branch-merge";

    @TempDir Path tmp;

    /** git 명령 하나의 결과 */
    record Run(int exit, String out) {
    }

    /** 임시 저장소 하나. 사람 한 명의 작업 폴더라고 본다 */
    static final class Repo {
        final Path dir;
        private int seq;

        Repo(Path dir) {
            this.dir = dir;
        }

        static Repo init(Path dir) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            Repo r = new Repo(dir);
            r.ok("init", "-q", "-b", "main");
            r.config();
            return r;
        }

        static Repo cloneOf(Path remote, Path dir) {
            Repo r = new Repo(dir.getParent());
            r.ok("clone", "-q", remote.toString(), dir.getFileName().toString());
            Repo c = new Repo(dir);
            c.config();
            return c;
        }

        void config() {
            ok("config", "user.name", "tester");
            ok("config", "user.email", "tester@example.com");
            ok("config", "commit.gpgsign", "false");
            ok("config", "core.autocrlf", "false");
            ok("config", "merge.conflictstyle", "merge");
        }

        Run git(String... args) {
            List<String> cmd = new ArrayList<>();
            cmd.add("git");
            cmd.addAll(List.of(args));
            try {
                Process p = new ProcessBuilder(cmd).directory(dir.toFile())
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                return new Run(p.waitFor(), out.strip().replace("\r", ""));
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        String ok(String... args) {
            Run r = git(args);
            assertThat(r.exit()).as("git %s\n%s", String.join(" ", args), r.out()).isZero();
            return r.out();
        }

        void write(String file, String content) {
            try {
                Path f = dir.resolve(file);
                Files.createDirectories(f.getParent());
                Files.writeString(f, content);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        String read(String file) {
            try {
                return Files.readString(dir.resolve(file)).replace("\r", "");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        /** 파일을 하나 고쳐 커밋한다. 커밋 시각을 늘려 가며 줘서 기록의 순서를 정한다 */
        void commit(String file, String content, String message) {
            write(file, content);
            ok("add", file);
            seq++;
            String date = "2026-11-25T10:%02d:00+09:00".formatted(seq);
            ok("-c", "user.name=tester", "commit", "-q", "-m", message, "--date", date);
        }

        int count(String ref) {
            return Integer.parseInt(ok("rev-list", "--count", ref));
        }
    }

    /** main에 첫 커밋, feature/coupon에 쿠폰 커밋 둘. withColleague면 그사이 main에 배송비 커밋 하나 */
    Repo couponSetup(Path dir, boolean withColleague) {
        Repo r = Repo.init(dir);
        r.commit("README.md", "쇼핑몰\n", "첫 커밋");
        r.ok("switch", "-q", "-c", "feature/coupon");
        r.commit("Coupon.java", "class Coupon {}\n", "쿠폰 클래스");
        r.commit("Coupon.java", "class Coupon { int rate; }\n", "쿠폰 할인율");
        r.ok("switch", "-q", "main");
        if (withColleague) {
            r.commit("Shipping.java", "class Shipping {}\n", "배송비 수정");
        }
        return r;
    }

    @Test
    @DisplayName("브랜치 파일에는 커밋 해시 한 줄이 있고, HEAD는 지금 브랜치를 가리킨다")
    void branchIsAFileWithAHash() throws IOException {
        Repo r = Repo.init(tmp.resolve("shop"));
        r.commit("README.md", "쇼핑몰\n", "첫 커밋");
        r.ok("branch", "feature/coupon");

        String refFile = Files.readString(r.dir.resolve(".git/refs/heads/feature/coupon")).strip();
        String head = Files.readString(r.dir.resolve(".git/HEAD")).strip();
        String revParse = r.ok("rev-parse", "feature/coupon");

        assertThat(refFile).hasSize(40).isEqualTo(revParse);
        assertThat(head).isEqualTo("ref: refs/heads/main");

        r.ok("switch", "-q", "feature/coupon");
        String mainBefore = r.ok("rev-parse", "main");
        r.commit("Coupon.java", "class Coupon {}\n", "쿠폰 클래스");
        String headAfter = Files.readString(r.dir.resolve(".git/HEAD")).strip();
        String couponAfter = Files.readString(r.dir.resolve(".git/refs/heads/feature/coupon")).strip();
        String mainAfter = Files.readString(r.dir.resolve(".git/refs/heads/main")).strip();
        String parent = r.ok("rev-parse", "feature/coupon^");

        assertThat(headAfter).isEqualTo("ref: refs/heads/feature/coupon");
        assertThat(mainAfter).isEqualTo(mainBefore);
        assertThat(parent).isEqualTo(mainBefore);
        assertThat(couponAfter).isNotEqualTo(mainBefore);

        CiOutput.record(LESSON, "refs", """
                $ cat .git/HEAD
                %s
                $ cat .git/refs/heads/feature/coupon
                %s
                $ git rev-parse feature/coupon
                %s

                (feature/coupon으로 옮겨 커밋 하나)
                $ cat .git/HEAD
                %s
                $ cat .git/refs/heads/feature/coupon
                %s
                $ cat .git/refs/heads/main
                %s  ← 그대로""".formatted(head, refFile, revParse, headAfter, couponAfter, mainAfter));
    }

    @Test
    @DisplayName("main이 움직이지 않았으면 merge는 fast-forward이고 커밋이 새로 생기지 않는다")
    void fastForward() {
        Repo r = couponSetup(tmp.resolve("ff"), false);
        int before = r.count("main") + 2;

        String out = r.ok("merge", "feature/coupon");

        assertThat(out).contains("Fast-forward");
        assertThat(r.count("main")).isEqualTo(3).isEqualTo(before);
        assertThat(r.ok("rev-parse", "main")).isEqualTo(r.ok("rev-parse", "feature/coupon"));
        CiOutput.record(LESSON, "ff", """
                $ git merge feature/coupon
                %s

                $ git log --graph --format=%s
                %s""".formatted(firstLines(out, 2), r.ok("log", "--oneline", "--graph", "--format=%s")));
    }

    @Test
    @DisplayName("두 줄기가 갈라졌으면 merge는 부모가 둘인 병합 커밋을 만들어 커밋이 5개가 된다")
    void threeWayMerge() {
        Repo r = couponSetup(tmp.resolve("merge"), true);
        String mainTip = r.ok("rev-parse", "main");
        String couponTip = r.ok("rev-parse", "feature/coupon");

        r.ok("merge", "--no-edit", "feature/coupon");

        String commit = r.ok("cat-file", "-p", "HEAD");
        List<String> parents = commit.lines().filter(l -> l.startsWith("parent ")).toList();
        assertThat(r.count("main")).isEqualTo(5);
        assertThat(parents).containsExactly("parent " + mainTip, "parent " + couponTip);
        CiOutput.record(LESSON, "merge", """
                $ git merge --no-edit feature/coupon
                $ git log --oneline | wc -l
                %d

                $ git log --graph --format=%s
                %s

                $ git cat-file -p HEAD
                %s""".formatted(r.count("main"),
                r.ok("log", "--graph", "--format=%s"), firstLines(commit, 3)));
    }

    @Test
    @DisplayName("rebase는 쿠폰 커밋을 새로 만들어 해시가 바뀌고, 그 뒤 merge는 fast-forward다")
    void rebaseMakesNewCommits() {
        Repo r = couponSetup(tmp.resolve("rebase"), true);
        String before = r.ok("log", "--format=%h %s", "main..feature/coupon");

        r.ok("switch", "-q", "feature/coupon");
        r.ok("rebase", "-q", "main");
        String after = r.ok("log", "--format=%h %s", "main..feature/coupon");
        r.ok("switch", "-q", "main");
        String merge = r.ok("merge", "feature/coupon");

        List<String> beforeHashes = before.lines().map(l -> l.split(" ")[0]).toList();
        List<String> afterHashes = after.lines().map(l -> l.split(" ")[0]).toList();
        List<String> messages = before.lines().map(l -> l.substring(l.indexOf(' ') + 1)).toList();
        assertThat(after.lines().map(l -> l.substring(l.indexOf(' ') + 1)).toList()).isEqualTo(messages);
        assertThat(afterHashes).doesNotContainAnyElementsOf(beforeHashes);
        assertThat(merge).contains("Fast-forward");
        assertThat(r.count("main")).isEqualTo(4);
        CiOutput.record(LESSON, "rebase", """
                (rebase 전) 쿠폰 커밋
                %s

                $ git rebase main
                (rebase 뒤) 쿠폰 커밋
                %s

                $ git switch main && git merge feature/coupon
                %s

                $ git log --graph --format=%s
                %s""".formatted(before, after, firstLines(merge, 2),
                r.ok("log", "--graph", "--format=%s")));
    }

    @Test
    @DisplayName("올린 브랜치를 rebase한 뒤 push는 거절되고, 남의 커밋이 생긴 뒤에는 --force-with-lease도 거절된다")
    void pushAfterRebaseIsRejected() {
        Path remote = tmp.resolve("remote.git");
        new Repo(tmp).ok("init", "-q", "--bare", "-b", "main", remote.toString());

        Repo me = Repo.cloneOf(remote, tmp.resolve("me"));
        me.commit("README.md", "쇼핑몰\n", "첫 커밋");
        me.ok("push", "-q", "origin", "main");
        me.ok("switch", "-q", "-c", "feature/coupon");
        me.commit("Coupon.java", "class Coupon {}\n", "쿠폰 클래스");
        me.ok("push", "-q", "-u", "origin", "feature/coupon");

        Repo colleague = Repo.cloneOf(remote, tmp.resolve("colleague"));
        colleague.commit("Shipping.java", "class Shipping {}\n", "배송비 수정");
        colleague.ok("push", "-q", "origin", "main");

        me.ok("fetch", "-q");
        me.ok("rebase", "-q", "origin/main");
        Run plain = me.git("push", "origin", "feature/coupon");

        colleague.ok("fetch", "-q");
        colleague.ok("switch", "-q", "feature/coupon");
        colleague.commit("Coupon.java", "class Coupon { int rate; }\n", "리뷰 반영");
        colleague.ok("push", "-q", "origin", "feature/coupon");
        Run lease = me.git("push", "--force-with-lease", "origin", "feature/coupon");

        assertThat(plain.exit()).isNotZero();
        assertThat(plain.out()).contains("rejected").contains("non-fast-forward");
        assertThat(lease.exit()).isNotZero();
        assertThat(lease.out()).contains("rejected").contains("stale info");
        CiOutput.record(LESSON, "push-rejected", """
                $ git rebase origin/main
                $ git push origin feature/coupon
                %s

                (그사이 동료가 feature/coupon에 커밋을 올림)
                $ git push --force-with-lease origin feature/coupon
                %s""".formatted(rejectLines(plain.out()), rejectLines(lease.out())));
    }

    @Test
    @DisplayName("같은 줄을 다르게 고치면 충돌하고, 떨어진 다른 줄을 고치면 충돌 없이 합쳐진다")
    void conflictOnlyOnSameLines() {
        String base = """
                class OrderService {
                  int fee = 3500;

                  int total(int price) {
                    return price + fee;
                  }

                  // 쿠폰은 아직 없음
                }
                """;

        Repo same = Repo.init(tmp.resolve("same"));
        same.commit("OrderService.java", base, "첫 커밋");
        same.ok("switch", "-q", "-c", "feature/coupon");
        same.commit("OrderService.java", base.replace("3500", "2500"), "쿠폰: 배송비 2500");
        same.ok("switch", "-q", "main");
        same.commit("OrderService.java", base.replace("3500", "3000"), "배송비 3000");
        Run conflict = same.git("merge", "feature/coupon");
        String status = same.ok("status", "--short");
        String file = same.read("OrderService.java");

        assertThat(conflict.exit()).isNotZero();
        assertThat(conflict.out()).contains("CONFLICT (content)");
        assertThat(status).isEqualTo("UU OrderService.java");
        assertThat(file).contains("<<<<<<< HEAD\n  int fee = 3000;\n=======\n  int fee = 2500;\n>>>>>>> feature/coupon");

        same.write("OrderService.java", base.replace("3500", "2500"));
        same.ok("add", "OrderService.java");
        same.ok("commit", "-q", "--no-edit");
        assertThat(same.count("main")).isEqualTo(4);

        Repo apart = Repo.init(tmp.resolve("apart"));
        apart.commit("OrderService.java", base, "첫 커밋");
        apart.ok("switch", "-q", "-c", "feature/coupon");
        apart.commit("OrderService.java", base.replace("// 쿠폰은 아직 없음", "int coupon = 1000;"), "쿠폰 필드");
        apart.ok("switch", "-q", "main");
        apart.commit("OrderService.java", base.replace("3500", "3000"), "배송비 3000");
        Run clean = apart.git("merge", "--no-edit", "feature/coupon");
        String merged = apart.read("OrderService.java");

        assertThat(clean.exit()).isZero();
        assertThat(merged).contains("int fee = 3000;").contains("int coupon = 1000;");

        Repo deleted = Repo.init(tmp.resolve("deleted"));
        deleted.commit("OrderService.java", base, "첫 커밋");
        deleted.ok("switch", "-q", "-c", "feature/coupon");
        deleted.commit("OrderService.java", base.replace("  int fee = 3500;\n", ""), "배송비 줄 지움");
        deleted.ok("switch", "-q", "main");
        deleted.commit("OrderService.java", base.replace("3500", "3000"), "배송비 3000");
        Run deleteConflict = deleted.git("merge", "feature/coupon");
        assertThat(deleteConflict.exit()).isNotZero();
        assertThat(deleteConflict.out()).contains("CONFLICT (content)");

        CiOutput.record(LESSON, "conflict", """
                (main은 배송비를 3000, feature/coupon은 2500으로 고침)
                $ git merge feature/coupon
                %s
                $ git status --short
                %s

                $ cat OrderService.java
                %s""".formatted(conflict.out(), status, file.strip()));
        CiOutput.record(LESSON, "no-conflict", """
                (main은 배송비 줄, feature/coupon은 맨 아래 줄을 고침)
                $ git merge --no-edit feature/coupon
                %s

                $ cat OrderService.java
                %s""".formatted(firstLines(clean.out(), 1), merged.strip()));
    }

    @Test
    @DisplayName("한쪽이 메서드 이름을 바꾸고 다른 쪽이 옛 이름을 부르면 충돌 없이 합쳐지고 컴파일이 깨진다")
    void cleanMergeCanBreakCompile() {
        String price = "public class Price {\n  public static int total(int p) {\n    return p + 3000;\n  }\n}\n";
        Repo r = Repo.init(tmp.resolve("semantic"));
        r.commit("Price.java", price, "첫 커밋");
        r.ok("switch", "-q", "-c", "feature/coupon");
        r.commit("Coupon.java", """
                public class Coupon {
                  public static int pay(int p) {
                    return Price.total(p) - 1000;
                  }
                }
                """, "쿠폰 결제");
        r.ok("switch", "-q", "main");
        r.commit("Price.java", price.replace("total", "withShipping"), "메서드 이름 바꿈");

        Run merge = r.git("merge", "--no-edit", "feature/coupon");
        JavaSource.Result compiled = JavaSource.compile(Map.of(
                "Price", r.read("Price.java"), "Coupon", r.read("Coupon.java")));

        assertThat(merge.exit()).isZero();
        assertThat(merge.out()).doesNotContain("CONFLICT");
        assertThat(compiled.ok()).isFalse();
        assertThat(String.join("\n", compiled.errors())).contains("total");
        CiOutput.record(LESSON, "semantic", """
                (main: Price.total → Price.withShipping
                 feature/coupon: Coupon.java에서 Price.total 호출)
                $ git merge --no-edit feature/coupon
                %s

                $ javac Price.java Coupon.java
                %s""".formatted(firstLines(merge.out(), 1), String.join("\n", compiled.errors())));
    }

    @Test
    @DisplayName("revert는 되돌리는 커밋을 하나 더 만들고 파일을 그 커밋 전으로 돌린다")
    void revertAddsACommit() {
        Repo r = Repo.init(tmp.resolve("revert"));
        r.commit("Shipping.java", "class Shipping { int fee = 3000; }\n", "첫 커밋");
        r.commit("Shipping.java", "class Shipping { int fee = 0; }\n", "배송비 0원 (실수)");
        String bad = r.ok("rev-parse", "--short", "HEAD");

        r.ok("revert", "--no-edit", bad);

        assertThat(r.count("main")).isEqualTo(3);
        assertThat(r.read("Shipping.java")).contains("fee = 3000");
        CiOutput.record(LESSON, "revert", """
                $ git revert %s
                $ git log --format=%s
                %s

                $ cat Shipping.java
                %s""".formatted(bad, r.ok("log", "--format=%s"), r.read("Shipping.java").strip()));
    }

    private static String firstLines(String text, int n) {
        return String.join("\n", text.lines().limit(n).toList());
    }

    /** push 거절 출력에서 주소처럼 실행마다 다른 줄을 뺀다 */
    private static String rejectLines(String text) {
        return String.join("\n", text.lines()
                .filter(l -> l.contains("rejected") || l.startsWith("error:") || l.startsWith("hint: Updates"))
                .map(l -> l.replaceAll("'[^']*remote\\.git'", "'remote.git'"))
                .toList());
    }
}
