package doc.batch.scheduler;

import doc.batch.controller.service.BatchScheduleVo;
import doc.batch.controller.service.mapper.BatchScheduleMapper;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * DB 기반 동적 스케줄러
 *
 * - 1분마다 DB를 폴링해서 크론 표현식 변경 감지 → 즉시 재등록
 * - ShedLock으로 멀티 인스턴스 중복 실행 방지
 * - 수동 reloadSchedules() 호출로 즉시 반영 가능
 */

@Component
public class DynamicScheduler implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(DynamicScheduler.class);

    private final BatchScheduleMapper            scheduleMapper;
    private final BatchJobLauncher               batchJobLauncher;
    private final TaskScheduler                  taskScheduler;

    private final Map<String, Runnable>           jobMap          = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks  = new ConcurrentHashMap<>();
    private final Map<String, String>             registeredCrons = new ConcurrentHashMap<>(); // 변경 감지용

    public DynamicScheduler(BatchScheduleMapper scheduleMapper,
                            BatchJobLauncher batchJobLauncher,
                            TaskScheduler taskScheduler) {
        this.scheduleMapper   = scheduleMapper;
        this.batchJobLauncher = batchJobLauncher;
        this.taskScheduler    = taskScheduler;

        // Job 추가 시 여기에 한 줄 추가
        jobMap.put("documentSyncJob",     batchJobLauncher::documentSyncJob);
        jobMap.put("documentApprovalJob", batchJobLauncher::documentApprovalJob);
        jobMap.put("documentReportJob",   batchJobLauncher::documentReportJob);
    }

    // 앱 시작 시 최초 등록
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        reloadSchedules();
    }

    // 1분마다 DB 폴링 → 크론이 바뀐 Job만 재등록
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "reloadBatchSchedules", lockAtMostFor = "55s")
    public void reloadSchedules() {
        try {
            List<BatchScheduleVo> schedules = scheduleMapper.selectActiveSchedules();

            for (BatchScheduleVo schedule : schedules) {
                String   jobName  = schedule.getJobName();
                String   cronExpr = schedule.getCronExpr();
                Runnable runnable = jobMap.get(jobName);

                if (runnable == null) {
                    log.warn("[DynamicScheduler] jobMap 미등록 Job: {}", jobName);
                    continue;
                }

                // 크론 동일 + 태스크 살아있으면 SKIP → 타이밍 문제 없음
                String         currentCron = registeredCrons.get(jobName);
                ScheduledFuture<?> currentTask = scheduledTasks.get(jobName);
                if (cronExpr.equals(currentCron) && currentTask != null && !currentTask.isCancelled()) {
                    log.debug("[DynamicScheduler] 변경 없음 SKIP: {} → {}", jobName, cronExpr);
                    continue;
                }

                // 크론 변경됐거나 태스크 없으면 → 기존 취소 후 재등록
                if (currentTask != null && !currentTask.isCancelled()) {
                    currentTask.cancel(false);
                    log.info("[DynamicScheduler] 취소: {} ({})", jobName, currentCron);
                }

                ScheduledFuture<?> future = taskScheduler.schedule(runnable, new CronTrigger(cronExpr));
                scheduledTasks.put(jobName, future);
                registeredCrons.put(jobName, cronExpr);
                log.info("[DynamicScheduler] 등록: {} → {}", jobName, cronExpr);
            }

        } catch (Exception e) {
            log.error("[DynamicScheduler] 스케줄 재등록 실패", e);
        }
    }
}