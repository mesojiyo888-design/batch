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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * 배치 Job 실행 공통 서비스
 * - 스케줄러, Controller 양쪽에서 동일하게 사용
 * - 실행 이력(BATCH_RUN_HIST) 자동 기록
 *
 * 변경점: Job 파라미터에 instanceId 를 "식별 제외(false)" 로 추가
 *   → 시작 시 정리(BatchApplication)에서 "내 서버가 실행하던 execution" 만 골라낼 수 있다.
 *   → identifying=false 라서 Job 인스턴스 구분(timestamp)에는 영향 없음
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
            Job job = context.getBean(jobName, Job.class);

            JobParametersBuilder builder = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("triggeredBy", triggeredBy)
                    .addString("triggerType", triggerType)
                    .addString("instanceId", instanceId, false);   // ★ 소유 서버 꼬리표 (식별 제외)

            if (targetStep != null && !targetStep.isEmpty()) {
                builder.addString(DocumentReportJobConfig.PARAM_TARGET_STEP, targetStep);
            }

            JobParameters params = builder.toJobParameters();
            execution = jobLauncher.run(job, params);

            hist.setExecutionId(execution.getId());
            hist.setStatus(execution.getStatus().name());
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
}
