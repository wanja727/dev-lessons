package lab.devlessons.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lab.devlessons.support.CiOutput;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;

/** 레슨 "메시지 큐 기초 (Kafka)"의 주장을 실제 Kafka 3.8 브로커에서 검증한다. */
class KafkaBasicsTest {

    static final String LESSON = "kafka-basics";
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");
    static KafkaProducer<String, String> producer;

    @BeforeAll
    static void start() {
        KAFKA.start();
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(p);
    }

    @AfterAll
    static void stop() {
        producer.close();
        KAFKA.stop();
    }

    static String newTopic(int partitions) throws Exception {
        String name = "orders-" + UUID.randomUUID().toString().substring(0, 8);
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(name, partitions, (short) 1))).all().get(30, TimeUnit.SECONDS);
        }
        return name;
    }

    static KafkaConsumer<String, String> consumer(String group) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        p.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // 오프셋 커밋을 직접 한다
        return new KafkaConsumer<>(p);
    }

    /** 원하는 개수를 받거나 시간이 다 될 때까지 읽는다 */
    static List<ConsumerRecord<String, String>> pollUntil(KafkaConsumer<String, String> c, int count, Duration max) {
        List<ConsumerRecord<String, String>> out = new ArrayList<>();
        long deadline = System.nanoTime() + max.toNanos();
        while (out.size() < count && System.nanoTime() < deadline) {
            ConsumerRecords<String, String> recs = c.poll(Duration.ofMillis(200));
            recs.forEach(out::add);
        }
        return out;
    }

    @Test
    @DisplayName("같은 키의 메시지는 같은 파티션으로 가고, 그 안에서 보낸 순서대로 읽힌다")
    void sameKeySamePartitionInOrder() throws Exception {
        String topic = newTopic(3);
        for (int i = 1; i <= 5; i++) {
            for (String order : List.of("order-1", "order-2", "order-3")) {
                producer.send(new ProducerRecord<>(topic, order, order + " 이벤트 " + i)).get();
            }
        }

        List<ConsumerRecord<String, String>> recs;
        try (KafkaConsumer<String, String> c = consumer("g-" + topic)) {
            c.subscribe(List.of(topic));
            recs = pollUntil(c, 15, Duration.ofSeconds(30));
        }

        assertThat(recs).hasSize(15);
        Map<String, Set<Integer>> partitionsByKey = new TreeMap<>();
        Map<String, List<String>> valuesByKey = new TreeMap<>();
        for (ConsumerRecord<String, String> r : recs) {
            partitionsByKey.computeIfAbsent(r.key(), k -> new HashSet<>()).add(r.partition());
            valuesByKey.computeIfAbsent(r.key(), k -> new ArrayList<>()).add(r.value().replace(r.key() + " ", ""));
        }
        partitionsByKey.values().forEach(ps -> assertThat(ps).hasSize(1));
        valuesByKey.values().forEach(vs -> assertThat(vs).containsExactly("이벤트 1", "이벤트 2", "이벤트 3", "이벤트 4", "이벤트 5"));
        StringBuilder sb = new StringBuilder("[파티션 3개인 토픽에 주문 3개의 이벤트 5개씩, 섞어서 보냄]\n");
        partitionsByKey.forEach((k, ps) -> sb.append(k).append(" → 파티션 ").append(ps).append(", 읽은 순서 ").append(valuesByKey.get(k)).append('\n'));
        CiOutput.record(LESSON, "key-order", sb.toString().trim());
    }

    @Test
    @DisplayName("같은 컨슈머 그룹은 파티션을 나눠 가진다. 파티션 3개에 컨슈머 4개면 하나는 놀고, 다른 그룹은 모든 메시지를 따로 받는다")
    void consumerGroupSplitsPartitions() throws Exception {
        String topic = newTopic(3);
        String group = "shipping-" + topic;
        int consumers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(consumers);
        AtomicBoolean stop = new AtomicBoolean();
        Map<Integer, Set<Integer>> assigned = new HashMap<>();
        CountDownLatch subscribed = new CountDownLatch(consumers);
        List<Future<?>> fs = new ArrayList<>();
        for (int i = 0; i < consumers; i++) {
            int id = i;
            fs.add(pool.submit(() -> {
                try (KafkaConsumer<String, String> c = consumer(group)) {
                    c.subscribe(List.of(topic));
                    subscribed.countDown();
                    while (!stop.get()) {
                        c.poll(Duration.ofMillis(200));
                        Set<Integer> mine = new HashSet<>();
                        for (TopicPartition tp : c.assignment()) {
                            mine.add(tp.partition());
                        }
                        synchronized (assigned) {
                            assigned.put(id, mine);
                        }
                    }
                }
                return null;
            }));
        }
        subscribed.await(10, TimeUnit.SECONDS);
        // 배정이 안정될 때까지 기다린다: 4개 컨슈머의 파티션 합이 정확히 3개
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
        Map<Integer, Set<Integer>> snapshot = Map.of();
        while (System.nanoTime() < deadline) {
            synchronized (assigned) {
                snapshot = new TreeMap<>(assigned);
            }
            int total = snapshot.values().stream().mapToInt(Set::size).sum();
            Set<Integer> union = new HashSet<>();
            snapshot.values().forEach(union::addAll);
            if (snapshot.size() == consumers && total == 3 && union.size() == 3) {
                Thread.sleep(1_000); // 1초 더 같은지 본다
                synchronized (assigned) {
                    if (assigned.equals(snapshot)) {
                        break;
                    }
                }
            }
            Thread.sleep(200);
        }
        stop.set(true);
        for (Future<?> f : fs) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // 다른 그룹(알림 서비스)은 같은 메시지를 모두 받는다
        for (int i = 0; i < 6; i++) {
            producer.send(new ProducerRecord<>(topic, "order-" + i, "주문됨 " + i)).get();
        }
        int notifyReceived;
        try (KafkaConsumer<String, String> other = consumer("notification-" + topic)) {
            other.subscribe(List.of(topic));
            notifyReceived = pollUntil(other, 6, Duration.ofSeconds(30)).size();
        }

        assertThat(snapshot.values().stream().mapToInt(Set::size).sum()).isEqualTo(3);
        assertThat(snapshot.values().stream().filter(Set::isEmpty).count()).isEqualTo(1);
        assertThat(notifyReceived).isEqualTo(6);
        StringBuilder sb = new StringBuilder("[파티션 3개, 같은 그룹(배송)의 컨슈머 4개]\n");
        snapshot.forEach((id, ps) -> sb.append("컨슈머 ").append(id + 1).append(": 파티션 ").append(ps.isEmpty() ? "없음 (대기)" : ps).append('\n'));
        sb.append("\n[다른 그룹(알림)의 컨슈머 1개]\n메시지 6개 중 받은 수: ").append(notifyReceived);
        CiOutput.record(LESSON, "group", sb.toString());
    }

    @Test
    @DisplayName("처리한 뒤 커밋하기 전에 죽으면 같은 메시지를 다시 받는다 (최소 한 번). event_id로 거르면 한 번만 처리된다")
    void processThenCrashBeforeCommitRedelivers() throws Exception {
        String topic = newTopic(1);
        String group = "points-" + topic;
        producer.send(new ProducerRecord<>(topic, "user-1", "evt-001:+100점")).get();
        Set<String> processedIds = new HashSet<>();
        int naiveApplied = 0;
        int dedupApplied = 0;
        long firstOffset;
        long secondOffset;

        try (KafkaConsumer<String, String> a = consumer(group)) {
            a.subscribe(List.of(topic));
            ConsumerRecord<String, String> r = pollUntil(a, 1, Duration.ofSeconds(30)).get(0);
            firstOffset = r.offset();
            naiveApplied++;                                            // 포인트 적립 처리
            if (processedIds.add(r.value().split(":")[0])) {
                dedupApplied++;
            }
            // commitSync 전에 프로세스가 죽었다 (close만 하고 커밋하지 않음)
        }
        try (KafkaConsumer<String, String> b = consumer(group)) {    // 재시작한 컨슈머
            b.subscribe(List.of(topic));
            ConsumerRecord<String, String> r = pollUntil(b, 1, Duration.ofSeconds(30)).get(0);
            secondOffset = r.offset();
            naiveApplied++;
            if (processedIds.add(r.value().split(":")[0])) {
                dedupApplied++;
            }
            b.commitSync();
        }

        assertThat(secondOffset).isEqualTo(firstOffset);
        assertThat(naiveApplied).isEqualTo(2);
        assertThat(dedupApplied).isEqualTo(1);
        CiOutput.record(LESSON, "at-least-once", "[처리 → (커밋 전에 종료) → 재시작]\n"
                + "1번째 컨슈머가 받은 offset: " + firstOffset + "\n"
                + "재시작한 컨슈머가 받은 offset: " + secondOffset + " (같은 메시지)\n"
                + "그냥 처리: 포인트 " + naiveApplied + "번 적립\n"
                + "event_id로 거른 처리: 포인트 " + dedupApplied + "번 적립");
    }

    @Test
    @DisplayName("처리하기 전에 커밋하고 죽으면 그 메시지는 다시 오지 않는다 (최대 한 번, 유실)")
    void commitThenCrashLosesMessage() throws Exception {
        String topic = newTopic(1);
        String group = "points-" + topic;
        producer.send(new ProducerRecord<>(topic, "user-1", "evt-002:+100점")).get();

        try (KafkaConsumer<String, String> a = consumer(group)) {
            a.subscribe(List.of(topic));
            assertThat(pollUntil(a, 1, Duration.ofSeconds(30))).hasSize(1);
            a.commitSync(); // 먼저 커밋
            // 처리하기 전에 프로세스가 죽었다
        }
        int redelivered;
        try (KafkaConsumer<String, String> b = consumer(group)) {
            b.subscribe(List.of(topic));
            redelivered = pollUntil(b, 1, Duration.ofSeconds(8)).size();
        }

        assertThat(redelivered).isZero();
        CiOutput.record(LESSON, "at-most-once", "[커밋 → (처리 전에 종료) → 재시작]\n재시작한 컨슈머가 받은 메시지: " + redelivered + "개. 포인트는 한 번도 적립되지 않았다");
    }

    @Test
    @DisplayName("acks=all로 보내면 브로커가 저장한 뒤 파티션과 offset을 돌려준다")
    void producerGetsOffset() throws Exception {
        String topic = newTopic(1);

        RecordMetadata m1 = producer.send(new ProducerRecord<>(topic, "k", "첫 번째")).get();
        RecordMetadata m2 = producer.send(new ProducerRecord<>(topic, "k", "두 번째")).get();

        assertThat(m1.offset()).isEqualTo(0);
        assertThat(m2.offset()).isEqualTo(1);
    }
}
