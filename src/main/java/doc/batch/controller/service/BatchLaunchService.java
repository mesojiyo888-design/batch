package doc.batch.controller.service;

import doc.batch.controller.service.mapper.BatchScheduleMapper;
import doc.batch.job.DocumentReportJobConfig;
import doc.batch.job.GenericJobConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 배치 Job 실행 공통 서비스
 * - 스케줄러, Controller 양쪽에서 동일하게 사용
 * - 실행 이력(BATCH_RUN_HIST) 자동 기록
 */
@Service
public class BatchLaunchService {

    private static final Logger log = LoggerFactory.getLogger(BatchLaunchService.class);

    private final JobLauncher         jobLauncher;
    private final ApplicationContext  context;
    private final BatchScheduleMapper scheduleMapper;

    @Value("${batch.instance-id}")
    private String instanceId;

    public BatchLaunchService(JobLauncher jobLauncher,
                              ApplicationContext context,
                              BatchScheduleMapper scheduleMapper) {
        this.jobLauncher    = jobLauncher;
        this.context        = context;
        this.scheduleMapper = scheduleMapper;
    }

    /** Job 전체 실행 */
    public JobExecution launch(String jobName, String triggeredBy, String triggerType) {
        return launch(jobName, null, triggeredBy, triggerType);
    }

    /** 특정 Step만 실행 (documentReportJob 전용) */
    public JobExecution launch(String jobName, String targetStep,
                               String triggeredBy, String triggerType) {

        // 이력 등록
        BatchRunHistVo hist = new BatchRunHistVo();
        hist.setJobName(jobName);
        hist.setStepName(targetStep);
        hist.setTriggeredBy(triggeredBy);
        hist.setTriggerType(triggerType);
        hist.setInstanceId(instanceId);
        scheduleMapper.insertRunHist(hist);

        JobExecution execution = null;
        try {
            // NATIVE(하드코딩 3개 Job)인지, BATCH_SCHEDULE에 동적 등록된 Job인지 라우팅
            // - NATIVE 또는 BATCH_SCHEDULE 미등록(레거시 호환) → jobName과 동일한 Bean 이름의 Job 실행
            // - 그 외(SQL/PROCEDURE/API 등) → 공통 genericJob Bean을 targetJobName 파라미터로 실행
            BatchScheduleVo schedule = scheduleMapper.selectSchedule(jobName);
            boolean useGenericJob = (schedule != null && !schedule.isNative());

            Job job = context.getBean(useGenericJob ? "genericJob" : jobName, Job.class);

            JobParametersBuilder builder = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("triggeredBy", triggeredBy)
                    .addString("triggerType", triggerType);

            if (targetStep != null && !targetStep.isEmpty()) {
                builder.addString(DocumentReportJobConfig.PARAM_TARGET_STEP, targetStep);
            }

            if (useGenericJob) {
                builder.addString(GenericJobConfig.PARAM_TARGET_JOB_NAME, jobName);
            }

            JobParameters params = builder.toJobParameters();
            execution = jobLauncher.run(job, params);

            hist.setExecutionId(execution.getId());
            hist.setStatus(execution.getStatus().name());

            // Spring Batch는 Tasklet/Step 내부에서 발생한 예외를 밖으로 던지지 않고 JobExecution
            // 상태만 FAILED로 표시한 채 정상 반환한다(catch 블록까지 오지 않음). 그래서 여기서
            // JobExecution/StepExecution에 담긴 실패 예외를 직접 꺼내 errorMsg로 기록해야 한다.
            if (execution.getStatus() == BatchStatus.FAILED) {
                String failureMsg = extractFailureMessage(execution);
                hist.setErrorMsg(failureMsg != null
                        ? failureMsg.substring(0, Math.min(failureMsg.length(), 2000))
                        : "Job이 FAILED 상태로 종료되었으나 상세 예외 정보가 없습니다.");
            }

            scheduleMapper.updateRunHist(hist);

            log.info("[BatchLaunchService] {} targetStep={} → {}",
                    jobName, targetStep, execution.getStatus());

        } catch (Exception e) {
            log.error("[BatchLaunchService] {} 실행 실패", jobName, e);

            hist.setStatus("FAILED");
            String msg = e.getMessage();
            hist.setErrorMsg(msg != null ? msg.substring(0, Math.min(msg.length(), 2000)) : "Unknown error");
            scheduleMapper.updateRunHist(hist);
        }

        return execution;
    }

    /**
     * FAILED로 종료된 JobExecution에서 실패 원인 메시지를 모아 하나의 문자열로 만든다.
     * JobExecution.getAllFailureExceptions()는 Job 레벨 + 모든 StepExecution의 실패 예외를 모두 모아 반환한다.
     */
    private String extractFailureMessage(JobExecution execution) {
        List<Throwable> failures = execution.getAllFailureExceptions();
        if (failures == null || failures.isEmpty()) {
            return null;
        }
        return failures.stream()
                .map(t -> t.getClass().getSimpleName() + ": " + t.getMessage())
                .collect(Collectors.joining(" | "));
    }
}