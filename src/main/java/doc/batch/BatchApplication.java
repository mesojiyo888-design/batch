package doc.batch;

import doc.batch.controller.service.mapper.BatchScheduleMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;

import java.util.Date;

@SpringBootApplication
@EnableBatchProcessing
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
@MapperScan("doc.batch.**.mapper")
public class BatchApplication {

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
            // 1. 내 서버 이력 FAILED 처리
            scheduleMapper.updateAbandonedRunHist(instanceId);

            // 2. Spring Batch 메타 테이블 정리
            jobExplorer.getJobNames().forEach(jobName -> {
                try {
                    jobExplorer.findRunningJobExecutions(jobName)
                            .forEach(execution -> {
                                execution.setStatus(BatchStatus.FAILED);
                                execution.setExitStatus(ExitStatus.FAILED);
                                execution.setEndTime(new Date());
                                jobRepository.update(execution);
                            });
                } catch (Exception e) {
                    // ignore
                }
            });
        };
    }
}
