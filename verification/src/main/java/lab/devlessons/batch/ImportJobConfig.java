package lab.devlessons.batch;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.AbstractItemCountingItemStreamItemReader;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 레슨 "스프링 배치: 청크 처리와 재시작"의 예제 Job.
 * 숫자 1~25를 읽어(reader) 그대로 넘기고(processor) batch_target 테이블에 쓴다(writer). 청크 크기 10.
 */
@Configuration
public class ImportJobConfig {

    public static final int TOTAL = 25;
    public static final int CHUNK = 10;

    /** 테스트에서 특정 숫자를 처리할 때 실패하게 만드는 스위치 */
    @Bean
    FailureSwitch failureSwitch() {
        return new FailureSwitch();
    }

    /** writer가 받은 청크 크기 기록 */
    @Bean
    WriteLog writeLog() {
        return new WriteLog();
    }

    @Bean
    Job importJob(JobRepository jobRepository, Step importStep) {
        return new JobBuilder("importJob", jobRepository).start(importStep).build();
    }

    @Bean
    Step importStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                    NumberReader numberReader, JdbcTemplate jdbc, FailureSwitch failureSwitch, WriteLog writeLog) {
        return new StepBuilder("importStep", jobRepository)
                .<Integer, Integer>chunk(CHUNK, txManager)
                .reader(numberReader)
                .processor(failingProcessor(failureSwitch))
                .writer(writer(jdbc, writeLog))
                .build();
    }

    /** 7의 배수는 잘못된 데이터로 보고 건너뛰는(skip) Job */
    @Bean
    Job skipJob(JobRepository jobRepository, Step skipStep) {
        return new JobBuilder("skipJob", jobRepository).start(skipStep).build();
    }

    @Bean
    Step skipStep(JobRepository jobRepository, PlatformTransactionManager txManager,
                  NumberReader numberReader, JdbcTemplate jdbc, WriteLog writeLog) {
        return new StepBuilder("skipStep", jobRepository)
                .<Integer, Integer>chunk(CHUNK, txManager)
                .reader(numberReader)
                .processor(item -> {
                    if (item % 7 == 0) {
                        throw new IllegalArgumentException("잘못된 데이터: " + item);
                    }
                    return item;
                })
                .writer(writer(jdbc, writeLog))
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(5)
                .build();
    }

    /** 재시작하면 ExecutionContext에 저장된 읽은 개수부터 다시 읽는 리더. Step 실행마다 새로 만든다 */
    @Bean
    @StepScope
    NumberReader numberReader() {
        return new NumberReader();
    }

    private static ItemProcessor<Integer, Integer> failingProcessor(FailureSwitch failureSwitch) {
        return item -> {
            if (failureSwitch.failOn != null && failureSwitch.failOn.equals(item)) {
                throw new IllegalStateException("처리 중 실패: " + item);
            }
            return item;
        };
    }

    private static ItemWriter<Integer> writer(JdbcTemplate jdbc, WriteLog writeLog) {
        return chunk -> {
            writeLog.sizes.add(chunk.size());
            for (Integer value : chunk) {
                jdbc.update("insert into batch_target (value) values (?)", value);
            }
        };
    }

    public static class FailureSwitch {
        public volatile Integer failOn;
    }

    public static class WriteLog {
        public final List<Integer> sizes = new CopyOnWriteArrayList<>();
    }

    public static class NumberReader extends AbstractItemCountingItemStreamItemReader<Integer> {
        private final List<Integer> items = IntStream.rangeClosed(1, TOTAL).boxed().toList();
        private int index;

        public NumberReader() {
            setName("numberReader"); // ExecutionContext 키 접두어 (numberReader.read.count)
        }

        @Override
        protected Integer doRead() {
            return index < items.size() ? items.get(index++) : null;
        }

        @Override
        protected void jumpToItem(int itemIndex) {
            index = itemIndex; // 재시작: 이미 커밋된 개수만큼 건너뛴다
        }

        @Override
        protected void doOpen() {
        }

        @Override
        protected void doClose() {
        }
    }
}
