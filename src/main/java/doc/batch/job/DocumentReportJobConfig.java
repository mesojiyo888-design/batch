package doc.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.ExitStatus;

/**
 * documentReportJob - 보고서 생성 배치
 *
 * 특정 Step만 실행하는 방법:
 *   JobParameter "targetStep" 에 실행할 Step명 전달
 *   → targetStep이 없거나 "ALL"이면 전체 실행
 *   → targetStep = "reportStep2" 이면 Step2만 실행하고 나머지 SKIP
 *
 * Step1: 데이터 집계 (통계 테이블 갱신)
 * Step2: 보고서 파일 생성 (Excel / PDF)
 * Step3: 보고서 배포 (공유 폴더 복사 + 알림)
 */
@Configuration
public class DocumentReportJobConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentReportJobConfig.class);

    /** JobParameter 키 상수 */
    public static final String PARAM_TARGET_STEP = "targetStep";

    private final JobBuilderFactory  jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    public DocumentReportJobConfig(JobBuilderFactory jobBuilderFactory,
                                    StepBuilderFactory stepBuilderFactory) {
        this.jobBuilderFactory  = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
    }

    // ---------------------------------------------------------------
    // Job 정의
    // ---------------------------------------------------------------
    @Bean
    public Job documentReportJob() {
        return jobBuilderFactory.get("documentReportJob")
                .incrementer(new RunIdIncrementer())
                .start(reportStep1())
                .next(reportStep2())
                .next(reportStep3())
                .build();
    }

    // ---------------------------------------------------------------
    // Step1: 데이터 집계
    // ---------------------------------------------------------------
    @Bean
    public Step reportStep1() {
        return stepBuilderFactory.get("reportStep1")
                .tasklet(skipAwareTasklet("reportStep1", (contribution, chunkContext) -> {
                    log.info("[documentReportJob][Step1] 데이터 집계 시작");

                    // reportService.aggregateStats();

                    log.info("[documentReportJob][Step1] 완료");
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ---------------------------------------------------------------
    // Step2: 보고서 파일 생성
    // ---------------------------------------------------------------
    @Bean
    public Step reportStep2() {
        return stepBuilderFactory.get("reportStep2")
                .tasklet(skipAwareTasklet("reportStep2", (contribution, chunkContext) -> {
                    log.info("[documentReportJob][Step2] 보고서 파일 생성 시작");

                    // reportService.generateExcel();

                    log.info("[documentReportJob][Step2] 완료");
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ---------------------------------------------------------------
    // Step3: 보고서 배포
    // ---------------------------------------------------------------
    @Bean
    public Step reportStep3() {
        return stepBuilderFactory.get("reportStep3")
                .tasklet(skipAwareTasklet("reportStep3", (contribution, chunkContext) -> {
                    log.info("[documentReportJob][Step3] 보고서 배포 시작");

                    // reportService.distributeReport();

                    log.info("[documentReportJob][Step3] 완료");
                    return RepeatStatus.FINISHED;
                }))
                .build();
    }

    // ---------------------------------------------------------------
    // 핵심: targetStep 파라미터 기반 SKIP 처리 Tasklet 래퍼
    //
    // targetStep 파라미터가 없거나 "ALL" → 모든 Step 실행
    // targetStep = "reportStep2"        → Step2만 실행, 나머지 SKIP
    // ---------------------------------------------------------------
    private Tasklet skipAwareTasklet(String myStepName, Tasklet delegate) {
        return (contribution, chunkContext) -> {
            String targetStep = (String) chunkContext.getStepContext()
                    .getJobParameters()
                    .get(PARAM_TARGET_STEP);

            boolean runAll = (targetStep == null || "ALL".equalsIgnoreCase(targetStep));

            if (!runAll && !myStepName.equals(targetStep)) {
                log.info("[documentReportJob][{}] targetStep={} → SKIP", myStepName, targetStep);
                // SKIP 처리: 아무것도 안 하고 FINISHED 반환
                // → Spring Batch는 COMPLETED로 기록되지만 실제 로직은 실행 안 됨
                contribution.setExitStatus(new ExitStatus("SKIPPED"));
                return RepeatStatus.FINISHED;
            }

            return delegate.execute(contribution, chunkContext);
        };
    }
}
