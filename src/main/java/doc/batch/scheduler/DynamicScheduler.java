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

    private final Map<String, ScheduledFuture<?>> scheduledTasks  = new ConcurrentHashMap<>();
    private final Map<String, String>             registeredCrons = new ConcurrentHashMap<>(); // 변경 감지용

    public DynamicScheduler(BatchScheduleMapper scheduleMapper,
                            BatchJobLauncher batchJobLauncher,
                            TaskScheduler taskScheduler) {
        this.scheduleMapper   = scheduleMapper;
        this.batchJobLauncher = batchJobLauncher;
        this.taskScheduler    = taskScheduler;
        // NATIVE/SQL/PROCEDURE 관계없이 모든 Job이 runScheduledJob(jobName) 공통 경로를 타므로
        // 여기에 Job별로 코드를 추가할 필요가 없다.
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
            List<BatchScheduleVo> schedules = scheduleMapper.selectActiveSchedulesForScheduler();

            for (BatchScheduleVo schedule : schedules) {
                String   jobName  = schedule.getJobName();
                String   cronExpr = schedule.getCronExpr();

                // NATIVE/SQL/PROCEDURE 관계없이 동일한 공통 트리거 사용.
                // 실제 Job 해석(자바 Bean 조회 vs genericJob 위임)은 BatchLaunchService가 담당하므로
                // 신규 Job이 추가돼도 여기 코드는 손댈 필요가 없다.
                Runnable runnable = () -> batchJobLauncher.runScheduledJob(jobName);

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

            // DB에서 삭제되었거나 비활성화(USE_YN='N')된 Job은 위 루프에서 아예 조회되지 않으므로,
            // 여기서 별도로 찾아서 취소해야 한다. 안 그러면 삭제/비활성 후에도 서버 재시작 전까지
            // 메모리에 남아있는 기존 크론 트리거가 계속 실행된다.
            java.util.Set<String> activeJobNames = new java.util.HashSet<>();
            for (BatchScheduleVo schedule : schedules) {
                activeJobNames.add(schedule.getJobName());
            }

            java.util.Iterator<Map.Entry<String, ScheduledFuture<?>>> it = scheduledTasks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ScheduledFuture<?>> entry = it.next();
                String jobName = entry.getKey();
                if (activeJobNames.contains(jobName)) {
                    continue;
                }
                ScheduledFuture<?> task = entry.getValue();
                if (task != null && !task.isCancelled()) {
                    task.cancel(false);
                    log.info("[DynamicScheduler] 삭제/비활성화된 Job 스케줄 취소: {}", jobName);
                }
                it.remove();
                registeredCrons.remove(jobName);
            }

        } catch (Exception e) {
            log.error("[DynamicScheduler] 스케줄 재등록 실패", e);
        }
    }
}