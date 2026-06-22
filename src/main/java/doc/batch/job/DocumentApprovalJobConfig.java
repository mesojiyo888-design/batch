package doc.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * documentApprovalJob - 결재 마감 처리 배치
 *
 * Step 3개 순차 실행 방식.
 * Step1 → Step2 → Step3 순서가 보장되며, 앞 Step 실패 시 뒤 Step 미실행.
 *
 * Step1: 마감 대상 결재건 추출 (상태 LOCK)
 * Step2: 결재 승인/반려 자동 처리
 * Step3: 알림 발송 + 이력 기록
 */
@Configuration
public class DocumentApprovalJobConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentApprovalJobConfig.class);

    private final JobBuilderFactory  jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    public DocumentApprovalJobConfig(JobBuilderFactory jobBuilderFactory,
                                      StepBuilderFactory stepBuilderFactory) {
        this.jobBuilderFactory  = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
    }

    // ---------------------------------------------------------------
    // Job 정의 - Step 순차 연결
    // ---------------------------------------------------------------
    @Bean
    public Job documentApprovalJob() {
        return jobBuilderFactory.get("documentApprovalJob")
                .incrementer(new RunIdIncrementer())
                .start(approvalStep1())   // Step1 실패 시 Step2, Step3 미실행
                .next(approvalStep2())
                .next(approvalStep3())
                .build();
    }

    // ---------------------------------------------------------------
    // Step1: 마감 대상 추출 및 잠금
    // Bean 이름 규칙: {jobName}_{stepName} 으로 명확히 구분
    // ---------------------------------------------------------------
    @Bean
    public Step approvalStep1() {
        return stepBuilderFactory.get("approvalStep1")
                .tasklet((contribution, chunkContext) -> {
                    log.info("[documentApprovalJob][Step1] 마감 대상 결재건 추출 시작");

                    // approvalService.lockPendingApprovals();
                    // → 기한 초과 결재건 STATUS = 'LOCKED' 처리

                    log.info("[documentApprovalJob][Step1] 완료");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    // ---------------------------------------------------------------
    // Step2: 자동 결재 처리
    // ---------------------------------------------------------------
    @Bean
    public Step approvalStep2() {
        return stepBuilderFactory.get("approvalStep2")
                .tasklet((contribution, chunkContext) -> {
                    log.info("[documentApprovalJob][Step2] 자동 결재 처리 시작");

                    // approvalService.processAutoApproval();
                    // → LOCKED 상태 결재건 자동 승인/반려

                    log.info("[documentApprovalJob][Step2] 완료");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    // ---------------------------------------------------------------
    // Step3: 알림 발송 및 이력 기록
    // ---------------------------------------------------------------
    @Bean
    public Step approvalStep3() {
        return stepBuilderFactory.get("approvalStep3")
                .tasklet((contribution, chunkContext) -> {
                    log.info("[documentApprovalJob][Step3] 알림 발송 시작");

                    // approvalService.sendNotifications();
                    // approvalService.recordHistory();

                    log.info("[documentApprovalJob][Step3] 완료");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }
}
