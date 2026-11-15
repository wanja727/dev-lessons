package lab.devlessons.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * 테스트가 실제로 낸 출력(콘솔 출력, SQL 로그, 측정값)을 레슨별 JSON으로 남긴다.
 * 배포 워크플로가 build/ci-output/*.json을 사이트의 /ci/로 복사하고, 레슨 페이지가 "실제 출력 보기"로 보여준다.
 */
public final class CiOutput {

    private static final Path DIR = Path.of("build", "ci-output");
    private static final ObjectMapper JSON = new ObjectMapper();

    private CiOutput() {
    }

    /** lesson: 레슨 페이지 이름(예: thread-pool), key: 페이지에서 고르는 출력 이름, text: 실제 출력 */
    public static synchronized void record(String lesson, String key, String text) {
        try {
            Files.createDirectories(DIR);
            Path file = DIR.resolve(lesson + ".json");
            ObjectNode root = Files.exists(file) ? (ObjectNode) JSON.readTree(file.toFile()) : JSON.createObjectNode();
            root.put("lesson", lesson);
            root.put("recordedAt", Instant.now().toString());
            root.put("javaVersion", System.getProperty("java.version"));
            root.put("commit", System.getenv().getOrDefault("GITHUB_SHA", "local"));
            ObjectNode outputs = root.has("outputs") ? (ObjectNode) root.get("outputs") : root.putObject("outputs");
            outputs.put(key, text);
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
