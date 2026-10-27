package doc.batch;

import doc.batch.controller.service.mapper.BatchScheduleMapper;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Date;

/**
 * 변경점
 *  - @MapperScan 제거: 다중 DB 라서 MainDataSourceConfig / BizDataSourceConfig 에서 DB별로 스캔
 *  - 시작 시 정리: "내 서버(instanceId)가 실행하던 것만" FAILED 처리 (다른 서버의 정상 실행은 건드리지 않음)
 *  - StepExecution 도 함께 FAILED 처리, execution 단위 try/catch + 로그
 */
@SpringBootApplication
@EnableBatchProcessing
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class BatchApplication {

    private static final Logger log = LoggerFactory.getLogger(BatchApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BatchApplication.class, args);
    }

    @Value("${batch.instance-id}")
    private String instanceId;

    // 앱 시작 시 미완료 이력 정리
    @Bean
    public ApplicationRunner cleanupOnStartup(BatchScheduleMapper scheduleMapper,
                                              JobExplorer jobExplorer,
                                              JobRepository jobRepository) {
        return args -> {
            // 1. 내 서버 이력(BATCH_RUN_HIST) FAILED 처리
            scheduleMapper.updateAbandonedRunHist(instanceId);

            // 2. Spring Batch 메타 테이블 정리 (내 서버가 실행하던 것만)
            try {
                for (String jobName : jobExplorer.getJobNames()) {
                    for (JobExecution execution : jobExplorer.findRunningJobExecutions(jobName)) {
                        // BatchLaunchService 가 Job 파라미터에 넣어 둔 instanceId 로 소유 서버를 판별
                        // owner == null 은 이 변경 이전에 실행된 건(꼬리표 없음)이므로 정리 대상에 포함
                        String owner = execution.getJobParameters().getString("instanceId");
                        if (owner != null && !owner.equals(instanceId)) {
                            continue;   // 다른 서버가 실행 중인 Job → 건드리지 않음
                        }
                        try {
                            abandon(execution, jobRepository);
                            log.warn("[startup-cleanup] {} executionId={} → FAILED 처리", jobName, execution.getId());
                        } catch (Exception e) {
                            log.error("[startup-cleanup] {} executionId={} 정리 실패", jobName, execution.getId(), e);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[startup-cleanup] 메타 테이블 정리 실패", e);
            }
        };
    }

    private static void abandon(JobExecution execution, JobRepository jobRepository) {
        Date now = new Date();
        String reason = "서버 비정상 종료로 중단 처리";

        // 실행 중이던 Step 도 같이 FAILED 처리
        for (StepExecution step : execution.getStepExecutions()) {
            if (step.getStatus().isRunning()) {
                step.setStatus(BatchStatus.FAILED);
                step.setExitStatus(ExitStatus.FAILED.addExitDescription(reason));
                step.setEndTime(now);
                jobRepository.update(step);
            }
        }

        execution.setStatus(BatchStatus.FAILED);
        execution.setExitStatus(ExitStatus.FAILED.addExitDescription(reason));
        execution.setEndTime(now);
        jobRepository.update(execution);
    }
}
