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

    private final BatchScheduleMapper scheduleMapper;
    private final BatchJobLauncher    batchJobLauncher;
    private final TaskScheduler       taskScheduler;  // 주입받기

    private final Map<String, Runnable> jobMap = new HashMap<>();
    private ScheduledTaskRegistrar registrar;

    public DynamicScheduler(BatchScheduleMapper scheduleMapper,
                            BatchJobLauncher batchJobLauncher,
                            TaskScheduler taskScheduler) {  // 생성자 주입 추가
        this.scheduleMapper  = scheduleMapper;
        this.batchJobLauncher = batchJobLauncher;
        this.taskScheduler   = taskScheduler;

        jobMap.put("documentSyncJob",     batchJobLauncher::documentSyncJob);
        jobMap.put("documentApprovalJob", batchJobLauncher::documentApprovalJob);
        jobMap.put("documentReportJob",   batchJobLauncher::documentReportJob);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        this.registrar = registrar;
        registrar.setScheduler(taskScheduler);  // 주입받은 걸 사용
        reloadSchedules();
    }

    // ---------------------------------------------------------------
    // 1분마다 DB 폴링 → 크론 변경 감지 시 재등록
    // ShedLock으로 멀티 인스턴스 중 한 대만 실행
    // ---------------------------------------------------------------
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "reloadBatchSchedules", lockAtMostFor = "55s")
    public void reloadSchedules() {
        try {
            List<BatchScheduleVo> schedules = scheduleMapper.selectActiveSchedules();
            log.info("[DynamicScheduler] 스케줄 재등록 - {}건", schedules.size());

            registrar.destroy();

            for (BatchScheduleVo schedule : schedules) {
                String   jobName  = schedule.getJobName();
                Runnable runnable = jobMap.get(jobName);

                if (runnable == null) {
                    log.warn("[DynamicScheduler] jobMap에 없는 Job: {}", jobName);
                    continue;
                }

                registrar.addTriggerTask(runnable, new CronTrigger(schedule.getCronExpr()));
                log.info("[DynamicScheduler] 등록: {} → {}", jobName, schedule.getCronExpr());
            }

            registrar.afterPropertiesSet();

        } catch (Exception e) {
            log.error("[DynamicScheduler] 스케줄 재등록 실패", e);
        }
    }
}