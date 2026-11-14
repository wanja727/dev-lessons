package lab.devlessons.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import lab.devlessons.batch.ImportJobConfig.FailureSwitch;
import lab.devlessons.batch.ImportJobConfig.WriteLog;
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

/** 레슨 "스프링 배치: 청크 처리와 재시작"의 주장을 PostgreSQL에서 검증한다. */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class SpringBatchTest {

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

    @Test
    @DisplayName("청크 크기 10으로 25건을 처리하면 writer는 10, 10, 5건씩 세 번 호출되고 25건이 저장된다")
    void chunkSizes() throws Exception {
        JobExecution run = launcher.run(importJob, newParams());

        assertThat(run.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(writeLog.sizes).containsExactly(10, 10, 5);
        assertThat(rows()).isEqualTo(25);
    }

    @Test
    @DisplayName("17번째 항목에서 실패하면 첫 청크(1~10)는 커밋되어 남고, 실패한 청크(11~20)는 통째로 롤백된다")
    void failureRollsBackOnlyCurrentChunk() throws Exception {
        failureSwitch.failOn = 17;

        JobExecution run = launcher.run(importJob, newParams());

        assertThat(run.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(rows()).isEqualTo(10);
        assertThat(step(run).getWriteCount()).isEqualTo(10);
        assertThat(step(run).getExecutionContext().getInt("numberReader.read.count")).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 파라미터로 재시작하면 마지막 커밋 지점(10건) 다음부터 읽어 15건만 처리하고, 중복 없이 25건이 된다")
    void restartResumesFromLastCommit() throws Exception {
        JobParameters params = newParams();
        failureSwitch.failOn = 17;
        JobExecution first = launcher.run(importJob, params);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);

        failureSwitch.failOn = null; // 원인을 고치고
        JobExecution second = launcher.run(importJob, params); // 같은 파라미터로 다시

        assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(second.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
        assertThat(step(second).getReadCount()).isEqualTo(15);
        assertThat(rows()).isEqualTo(25); // batch_target.value가 기본 키라 중복이면 실패했을 것
    }

    @Test
    @DisplayName("이미 완료된 JobInstance는 같은 파라미터로 다시 실행할 수 없다")
    void completedInstanceCannotRerun() throws Exception {
        JobParameters params = newParams();
        launcher.run(importJob, params);

        assertThatThrownBy(() -> launcher.run(importJob, params))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    @DisplayName("skip 설정: 잘못된 데이터(7, 14, 21)는 건너뛰고 나머지 22건을 저장하며 Job은 성공한다")
    void skipBadItems() throws Exception {
        JobExecution run = launcher.run(skipJob, newParams());

        assertThat(run.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(rows()).isEqualTo(22);
        assertThat(step(run).getProcessSkipCount()).isEqualTo(3);
    }

    private static JobParameters newParams() {
        return new JobParametersBuilder().addString("run", UUID.randomUUID().toString()).toJobParameters();
    }

    private static StepExecution step(JobExecution run) {
        return run.getStepExecutions().iterator().next();
    }

    private int rows() {
        Integer n = jdbc.queryForObject("select count(*) from batch_target", Integer.class);
        return n == null ? 0 : n;
    }
}
