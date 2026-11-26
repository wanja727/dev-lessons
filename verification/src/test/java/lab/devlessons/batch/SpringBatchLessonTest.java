package lab.devlessons.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lab.devlessons.batch.ImportJobConfig.FailureSwitch;
import lab.devlessons.batch.ImportJobConfig.WriteLog;
import lab.devlessons.support.CiOutput;
import lab.devlessons.support.PostgresContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 레슨 "스프링 배치"(S15)가 보여 줄 실제 출력을 남긴다. 주장 자체는 예전 테스트 SpringBatchTest가 확인하고,
 * 여기서는 같은 Job(ImportJobConfig)을 돌려 커밋 수와 메타데이터 테이블의 값까지 기록한다.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SpringBatchLessonTest {

    private static final String LESSON = "spring-batch";

    @Autowired JobLauncher launcher;
    @Autowired @Qualifier("importJob") Job importJob;
    @Autowired @Qualifier("skipJob") Job skipJob;
    @Autowired JdbcTemplate jdbc;
    @Autowired FailureSwitch failureSwitch;
    @Autowired WriteLog writeLog;

    @BeforeEach
    void setUp() {
        jdbc.execute("create table if not exists batch_target (value int primary key)");
        jdbc.execute("delete from batch_target");
        failureSwitch.failOn = null;
        writeLog.sizes.clear();
    }

    private static JobParameters params() {
        return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
    }

    private static StepExecution step(JobExecution run) {
        return run.getStepExecutions().iterator().next();
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from batch_target", Integer.class);
    }

    @Test
    @DisplayName("청크 10으로 25건: writer는 10, 10, 5건, 커밋은 청크 수만큼")
    void chunks() throws Exception {
        JobExecution run = launcher.run(importJob, params());
        StepExecution s = step(run);

        assertThat(writeLog.sizes).containsExactly(10, 10, 5);
        assertThat(rows()).isEqualTo(25);
        CiOutput.record(LESSON, "chunks", "청크 크기 10, 읽을 항목 25건\nwriter가 받은 건수: " + writeLog.sizes
                + "\n읽음 " + s.getReadCount() + ", 씀 " + s.getWriteCount() + ", 커밋 " + s.getCommitCount() + "번"
                + "\n상태: " + run.getStatus() + ", 저장된 행: " + rows());
    }

    @Test
    @DisplayName("17번째에서 실패하면 1~10만 남고, 같은 파라미터로 다시 실행하면 11번부터 읽어 15건만 처리한다")
    void failAndRestart() throws Exception {
        JobParameters p = params();
        failureSwitch.failOn = 17;
        JobExecution first = launcher.run(importJob, p);
        int afterFail = rows();
        StepExecution s1 = step(first);
        Map<String, Object> meta = jdbc.queryForMap(
                "select status, read_count, write_count, commit_count, rollback_count from batch_step_execution where step_execution_id = ?",
                s1.getId());

        failureSwitch.failOn = null;
        JobExecution second = launcher.run(importJob, p);
        StepExecution s2 = step(second);

        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(afterFail).isEqualTo(10);
        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
        assertThat(s2.getReadCount()).isEqualTo(15);
        assertThat(rows()).isEqualTo(25);
        String metaLine = List.of("status", "read_count", "write_count", "commit_count", "rollback_count").stream()
                .map(k -> k + "=" + meta.get(k)).collect(Collectors.joining(", "));
        CiOutput.record(LESSON, "restart", "첫 실행: 17번째 항목에서 실패 → 상태 " + first.getStatus()
                + "\n  저장된 행: " + afterFail + "개 (1~10)"
                + "\n  batch_step_execution: " + metaLine
                + "\n같은 파라미터로 다시 실행 → 상태 " + second.getStatus()
                + "\n  같은 JobInstance? " + (second.getJobInstance().getInstanceId() == first.getJobInstance().getInstanceId())
                + "\n  이번에 읽은 건수: " + s2.getReadCount() + " (11~25)"
                + "\n  저장된 행: " + rows() + "개 (중복 없음)");
    }

    @Test
    @DisplayName("완료된 JobInstance를 같은 파라미터로 다시 돌리면 JobInstanceAlreadyCompleteException, skip은 잘못된 3건을 건너뛴다")
    void completedAndSkip() throws Exception {
        JobParameters p = params();
        launcher.run(importJob, p);
        String again;
        try {
            launcher.run(importJob, p);
            again = "실행됨";
        } catch (JobInstanceAlreadyCompleteException e) {
            again = e.getClass().getSimpleName();
        }
        jdbc.execute("delete from batch_target");
        JobExecution skip = launcher.run(skipJob, params());

        assertThat(again).isEqualTo("JobInstanceAlreadyCompleteException");
        assertThat(skip.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step(skip).getProcessSkipCount()).isEqualTo(3);
        assertThatThrownBy(() -> launcher.run(importJob, p)).isInstanceOf(JobInstanceAlreadyCompleteException.class);
        CiOutput.record(LESSON, "complete-skip", "완료된 Job을 같은 파라미터로 다시 실행 → " + again
                + "\n\nskip(IllegalArgumentException), skipLimit 5, 7의 배수는 잘못된 데이터\n상태 " + skip.getStatus()
                + ", 건너뜀 " + step(skip).getProcessSkipCount() + "건, 저장된 행 " + rows() + "개");
    }
}
