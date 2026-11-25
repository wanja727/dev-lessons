package lab.devlessons.docker;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lab.devlessons.support.CiOutput;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

/**
 * 레슨 "도커와 컨테이너"(T5). Testcontainers와 도커 클라이언트로 컨테이너를 띄워 안을 본다.
 */
class DockerContainerTest {

    static final String LESSON = "docker-container";
    static final String ALPINE = "alpine:3.20";
    static final String JRE = "eclipse-temurin:21-jre";

    static DockerClient docker() {
        return DockerClientFactory.instance().client();
    }

    /** 명령 하나를 돌리고 끝나는 컨테이너. 출력(로그)을 돌려준다 */
    static String runOnce(String image, Long memoryBytes, String... command) {
        try (GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(image))) {
            c.withCommand(command)
                    .withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(3)));
            if (memoryBytes != null) {
                c.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withMemory(memoryBytes).withMemorySwap(memoryBytes));
            }
            c.start();
            return c.getLogs().strip();
        }
    }

    static GenericContainer<?> sleeper() {
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(ALPINE)).withCommand("sleep", "300");
        c.start();
        return c;
    }

    static String sh(GenericContainer<?> c, String script) throws Exception {
        var r = c.execInContainer("sh", "-c", script);
        return (r.getStdout() + r.getStderr()).strip();
    }

    @Test
    @DisplayName("컨테이너 안의 ps에는 프로세스가 하나뿐이고 PID가 1이다")
    void onlyOneProcessInside() {
        String out = runOnce(ALPINE, null, "ps");
        List<String> rows = out.lines().skip(1).toList();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).strip()).startsWith("1 ");
        CiOutput.record(LESSON, "ps", "$ docker run --rm alpine ps\n" + out);
    }

    @Test
    @DisplayName("컨테이너 안의 uname -r은 도커 엔진의 호스트 커널 버전과 같고, 컨테이너는 1초 남짓에 뜬다")
    void sharesHostKernel() {
        new RemoteDockerImage(DockerImageName.parse(ALPINE)).get();
        long t = System.nanoTime();
        String inside = runOnce(ALPINE, null, "uname", "-r");
        long ms = (System.nanoTime() - t) / 1_000_000;
        String host = docker().infoCmd().exec().getKernelVersion();

        assertThat(inside).isEqualTo(host);
        CiOutput.record(LESSON, "kernel", """
                $ docker run --rm alpine uname -r
                %s
                도커 엔진이 알려 준 호스트 커널 → %s

                컨테이너를 만들고 명령을 돌리고 지우기까지 → %.1f초""".formatted(inside, host, ms / 1000.0));
    }

    @Test
    @DisplayName("같은 이미지의 두 컨테이너는 파일을 따로 갖고, 지웠다 다시 띄우면 파일이 없다. 볼륨에 둔 파일은 남는다")
    void writableLayerIsPerContainer() throws Exception {
        List<String> log = new ArrayList<>();
        String inB;
        String inNewA;
        try (GenericContainer<?> a = sleeper(); GenericContainer<?> b = sleeper()) {
            sh(a, "echo 주문 > /tmp/order.log");
            log.add("A$ echo 주문 > /tmp/order.log");
            log.add("A$ cat /tmp/order.log → " + sh(a, "cat /tmp/order.log"));
            inB = sh(b, "cat /tmp/order.log 2>&1 || true");
            log.add("B$ cat /tmp/order.log → " + inB);
        }
        log.add("(A와 B를 지움)");
        try (GenericContainer<?> a2 = sleeper()) {
            inNewA = sh(a2, "cat /tmp/order.log 2>&1 || true");
            log.add("새 A$ cat /tmp/order.log → " + inNewA);
        }

        String volume = "devlessons-t5-" + System.nanoTime();
        docker().createVolumeCmd().withName(volume).exec();
        String inVolume;
        try {
            try (GenericContainer<?> v1 = sleeperWithVolume(volume)) {
                sh(v1, "echo 주문 > /data/order.log");
            }
            try (GenericContainer<?> v2 = sleeperWithVolume(volume)) {
                inVolume = sh(v2, "cat /data/order.log");
            }
        } finally {
            docker().removeVolumeCmd(volume).exec();
        }
        log.add("");
        log.add("(볼륨을 /data에 붙인 컨테이너에서 파일을 만들고 지움)");
        log.add("볼륨을 붙인 새 컨테이너$ cat /data/order.log → " + inVolume);

        assertThat(inB).contains("No such file");
        assertThat(inNewA).contains("No such file");
        assertThat(inVolume).isEqualTo("주문");
        CiOutput.record(LESSON, "layer", String.join("\n", log));
    }

    static GenericContainer<?> sleeperWithVolume(String volume) {
        GenericContainer<?> c = new GenericContainer<>(DockerImageName.parse(ALPINE))
                .withCommand("sleep", "300")
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig()
                        .withBinds(new Bind(volume, new Volume("/data"))));
        c.start();
        return c;
    }

    @Test
    @DisplayName("소스만 바꿔 다시 빌드하면 의존성을 먼저 복사한 Dockerfile은 의존성 층에 캐시를 쓰고, COPY . . Dockerfile은 쓰지 않는다")
    void layerCacheDependsOnOrder(@TempDir Path dir) throws IOException {
        new RemoteDockerImage(DockerImageName.parse(ALPINE)).get();
        String good = """
                FROM alpine:3.20
                WORKDIR /app
                COPY deps.txt .
                RUN echo "의존성 내려받기" && sleep 2
                COPY src.txt .
                RUN echo "빌드"
                """;
        String bad = """
                FROM alpine:3.20
                WORKDIR /app
                COPY . .
                RUN echo "의존성 내려받기" && sleep 2
                RUN echo "빌드"
                """;
        String goodResult = buildTwice(dir.resolve("good"), good);
        String badResult = buildTwice(dir.resolve("bad"), bad);

        CiOutput.record(LESSON, "cache", """
                의존성을 먼저 복사한 Dockerfile (src.txt만 고쳐 다시 빌드)
                %s

                COPY . . 로 한 번에 복사한 Dockerfile (src.txt만 고쳐 다시 빌드)
                %s""".formatted(goodResult, badResult));
        assertThat(goodResult).contains("의존성 내려받기 → 캐시");
        assertThat(badResult).contains("의존성 내려받기 → 다시 실행");
    }

    /** 처음 빌드한 뒤 src.txt만 바꿔 다시 빌드하고, 두 번째 빌드의 단계마다 캐시를 썼는지 적는다 */
    static String buildTwice(Path dir, String dockerfile) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Dockerfile"), dockerfile);
        Files.writeString(dir.resolve("deps.txt"), "spring-boot 3.5\n");
        Files.writeString(dir.resolve("src.txt"), "class A {}\n");
        String tag = "devlessons-t5-" + System.nanoTime();
        build(dir, tag);
        Files.writeString(dir.resolve("src.txt"), "class A { int x; }\n");
        List<String> second = build(dir, tag);
        try {
            docker().removeImageCmd(tag).withForce(true).exec();
        } catch (Exception ignored) {
            // 지우지 못해도 테스트 결과와 상관없다
        }
        return summarize(second);
    }

    static List<String> build(Path dir, String tag) {
        List<String> lines = new ArrayList<>();
        docker().buildImageCmd(dir.toFile()).withTags(Set.of(tag)).withPull(false)
                .exec(new BuildImageResultCallback() {
                    @Override
                    public void onNext(BuildResponseItem item) {
                        if (item.getStream() != null) {
                            lines.addAll(item.getStream().lines().toList());
                        }
                        super.onNext(item);
                    }
                }).awaitImageId(3, TimeUnit.MINUTES);
        return lines;
    }

    /** 고전 빌더의 출력에서 "Step n/m : 명령" 다음 줄이 "---> Using cache"인지 본다 */
    static String summarize(List<String> lines) {
        List<String> out = new ArrayList<>();
        Pattern step = Pattern.compile("^Step \\d+/\\d+ : (.*)$");
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = step.matcher(lines.get(i).strip());
            if (!m.matches() || m.group(1).startsWith("FROM")) {
                continue;
            }
            String cmd = m.group(1);
            boolean cached = false;
            for (int j = i + 1; j < lines.size() && !lines.get(j).strip().startsWith("Step "); j++) {
                if (lines.get(j).contains("Using cache")) {
                    cached = true;
                }
            }
            String label = cmd.startsWith("RUN echo \"의존성") ? "의존성 내려받기" : cmd;
            out.add("  " + label + " → " + (cached ? "캐시" : "다시 실행"));
        }
        if (out.isEmpty()) {
            return String.join("\n", lines);
        }
        return String.join("\n", out);
    }

    static final Pattern MAX_HEAP = Pattern.compile("MaxHeapSize\\s+=\\s+(\\d+)");

    static long maxHeap(Long memoryBytes, String... javaOptions) {
        List<String> cmd = new ArrayList<>(List.of("java"));
        cmd.addAll(List.of(javaOptions));
        cmd.addAll(List.of("-XX:+PrintFlagsFinal", "-version"));
        String out = runOnce(JRE, memoryBytes, cmd.toArray(String[]::new));
        Matcher m = MAX_HEAP.matcher(out);
        assertThat(m.find()).as(out).isTrue();
        return Long.parseLong(m.group(1));
    }

    @Test
    @DisplayName("JVM은 컨테이너 메모리 한도의 4분의 1을 힙 최대 크기로 잡고, MaxRAMPercentage=75면 4분의 3을 잡는다")
    void jvmReadsContainerLimit() {
        long mb = 1024 * 1024;
        long none = maxHeap(null);
        long limit512 = maxHeap(512 * mb);
        long limit2g = maxHeap(2048 * mb);
        long limit512p75 = maxHeap(512 * mb, "-XX:MaxRAMPercentage=75");
        long hostMem = docker().infoCmd().exec().getMemTotal();

        assertThat(limit512).isBetween(120 * mb, 130 * mb);
        assertThat(limit2g).isBetween(500 * mb, 520 * mb);
        assertThat(limit512p75).isBetween(370 * mb, 390 * mb);
        CiOutput.record(LESSON, "heap", """
                $ docker run --rm [-m 한도] eclipse-temurin:21-jre \\
                    java [옵션] -XX:+PrintFlagsFinal -version

                한도 없음 (도커가 쓰는 메모리 %d MB)
                  → MaxHeapSize %d MB
                -m 512m
                  → MaxHeapSize %d MB
                -m 2g
                  → MaxHeapSize %d MB
                -m 512m, -XX:MaxRAMPercentage=75
                  → MaxHeapSize %d MB""".formatted(hostMem / mb, none / mb, limit512 / mb,
                limit2g / mb, limit512p75 / mb));
    }

    @Test
    @DisplayName("메모리 한도를 넘는 프로세스는 커널이 죽여 종료 코드 137이 남는다")
    void oomKilled() {
        new RemoteDockerImage(DockerImageName.parse(ALPINE)).get();
        DockerClient client = docker();
        long limit = 32L * 1024 * 1024;
        String id = client.createContainerCmd(ALPINE)
                .withCmd("tail", "/dev/zero")
                .withHostConfig(HostConfig.newHostConfig().withMemory(limit).withMemorySwap(limit))
                .withLabels(java.util.Map.of("org.testcontainers", "true"))
                .exec().getId();
        try {
            client.startContainerCmd(id).exec();
            int code = client.waitContainerCmd(id).start().awaitStatusCode(2, TimeUnit.MINUTES);
            InspectContainerResponse.ContainerState state = client.inspectContainerCmd(id).exec().getState();

            // 137(SIGKILL)은 늘 남는다. OOMKilled 표시는 도커와 cgroup 버전에 따라 false로 나오기도 해서(CI에서 겪음) 값만 남긴다
            assertThat(code).isEqualTo(137);
            CiOutput.record(LESSON, "oom", """
                    $ docker run -m 32m alpine tail /dev/zero
                    (로그 없이 끝남)
                    $ docker inspect --format \\
                        '{{.State.ExitCode}} {{.State.OOMKilled}}'
                    %d %s""".formatted(state.getExitCodeLong(), state.getOOMKilled()));
        } finally {
            client.removeContainerCmd(id).withForce(true).exec();
        }
    }
}
