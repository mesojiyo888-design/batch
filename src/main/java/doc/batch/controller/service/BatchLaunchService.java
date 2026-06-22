package doc.batch.controller.service;

import doc.batch.controller.service.mapper.BatchScheduleMapper;
import doc.batch.job.DocumentReportJobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 배치 Job 실행 공통 서비스
 * - 스케줄러, Controller 양쪽에서 동일하게 사용
 * - 실행 이력(BATCH_RUN_HIST) 자동 기록
 */
@Service
public class BatchLaunchService {

    private static final Logger log = LoggerFactory.getLogger(BatchLaunchService.class);

    private final JobLauncher          jobLauncher;
    private final ApplicationContext   context;
    private final BatchScheduleMapper scheduleMapper;

    public BatchLaunchService(JobLauncher jobLauncher,
                               ApplicationContext context,
                               BatchScheduleMapper scheduleMapper) {
        this.jobLauncher    = jobLauncher;
        this.context        = context;
        this.scheduleMapper = scheduleMapper;
    }

    /**
     * Job 전체 실행
     *
     * @param jobName     Spring Bean 이름 (= Job 이름)
     * @param triggeredBy 실행자 ("scheduler" or userId)
     * @param triggerType "AUTO" or "MANUAL"
     */
    public JobExecution launch(String jobName, String triggeredBy, String triggerType) {
        log.info("### [DEBUG] launch 서비스 진입: {} ###", jobName); // 이 로그가 안 찍힌다면 스케줄러 문제
        return launch(jobName, null, triggeredBy, triggerType);
    }

    /**
     * 특정 Step만 실행 (documentReportJob 전용)
     *
     * @param jobName     Job Bean 이름
     * @param targetStep  실행할 Step 이름 (null or "ALL" 이면 전체 실행)
     * @param triggeredBy 실행자
     * @param triggerType "AUTO" or "MANUAL"
     */
    public JobExecution launch(String jobName, String targetStep,
                                String triggeredBy, String triggerType) {

        // ── 이력 등록 (RUNNING) ─────────────────────────────────────
        BatchRunHistVo hist = new BatchRunHistVo();
        hist.setJobName(jobName);
        hist.setStepName(targetStep);
        hist.setTriggeredBy(triggeredBy);
        hist.setTriggerType(triggerType);
        scheduleMapper.insertRunHist(hist);

        JobExecution execution = null;
        try {
            Job job = context.getBean(jobName, Job.class);

            JobParametersBuilder paramBuilder = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("triggeredBy", triggeredBy)
                    .addString("triggerType", triggerType);

            // targetStep 파라미터 추가 (documentReportJob에서 사용)
            if (targetStep != null && !targetStep.isEmpty()) {
                paramBuilder.addString(DocumentReportJobConfig.PARAM_TARGET_STEP, targetStep);
            }

            JobParameters params = paramBuilder.toJobParameters();
            execution = jobLauncher.run(job, params);

            // ── 이력 업데이트 (COMPLETED / FAILED) ───────────────────
            hist.setExecutionId(execution.getId());
            hist.setStatus(execution.getStatus().name());
            scheduleMapper.updateRunHist(hist);

            log.info("[BatchLaunchService] {} / targetStep={} → {}", jobName, targetStep, execution.getStatus());

        } catch (Exception e) {
            log.error("[BatchLaunchService] {} 실행 실패", jobName, e);

            hist.setStatus("FAILED");
            hist.setErrorMsg(e.getMessage() != null
                    ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 2000))
                    : "Unknown error");
            scheduleMapper.updateRunHist(hist);
        }

        return execution;
    }
}
