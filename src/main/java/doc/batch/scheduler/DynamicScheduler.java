package doc.batch.scheduler;

import doc.batch.controller.service.BatchScheduleVo;
import doc.batch.controller.service.mapper.BatchScheduleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * DB 기반 동적 스케줄러
 *
 * - 1분마다 DB를 폴링해서 크론 표현식 변경 감지 → 바뀐 Job 만 재등록
 * - 수동 reloadSchedules() 호출로 즉시 반영 가능 (BatchController)
 * - 멀티 인스턴스 중복 실행 방지는 BatchJobLauncher 의 @SchedulerLock 이 담당
 *
 * 변경점
 *  1. 시작 게이트(ready): BatchApplication 의 시작 시 정리가 끝나고 start() 가 호출되기 전에는
 *     어떤 Job 도 등록하지 않는다 → "정리가 방금 시작된 Job 을 FAILED 로 덮어쓰는" 경쟁 상태 제거
 *  2. reloadSchedules() synchronized: 시작 직후 동시 실행으로 같은 Job 이 두 번 등록되는 문제 방지
 *  3. reloadSchedules() 의 @SchedulerLock 제거: 인스턴스별 메모리 상태를 맞추는 작업이라 락이 필요 없음
 *  4. 비활성(USE_YN='N')/삭제된 스케줄은 실행 중인 태스크도 취소
 *  5. 잘못된 크론은 기존 스케줄을 유지한 채 로그만 남김 (다른 Job 등록에 영향 없음)
 *  6. 스케줄 한 건 단위 try/catch, 미등록 Job 경고는 1회만 출력
 */
@Component
public class DynamicScheduler {

    private static final Logger log = LoggerFactory.getLogger(DynamicScheduler.class);

    /** 크론 해석 기준 타임존 (서버 기본 타임존에 의존하지 않도록 고정) */
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final BatchScheduleMapper scheduleMapper;
    private final TaskScheduler       taskScheduler;

    private final Map<String, Runnable>           jobMap          = new HashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledTasks  = new ConcurrentHashMap<>();
    private final Map<String, String>             registeredCrons = new ConcurrentHashMap<>(); // 변경 감지용
    private final Set<String>                     warnedJobs      = new HashSet<>();           // 미등록 Job 경고 1회용
    private final Map<String, String>             invalidCrons    = new HashMap<>();           // 잘못된 크론 로그 1회용

    /** 시작 시 정리가 끝나기 전에는 false → 아무것도 등록하지 않는다 */
    private volatile boolean ready = false;

    public DynamicScheduler(BatchScheduleMapper scheduleMapper,
            BatchJobLauncher batchJobLauncher,
            TaskScheduler taskScheduler) {
        this.scheduleMapper = scheduleMapper;
        this.taskScheduler  = taskScheduler;

        // Job 추가 시 여기에 한 줄 추가
        jobMap.put("documentSyncJob",     batchJobLauncher::documentSyncJob);
        jobMap.put("documentApprovalJob", batchJobLauncher::documentApprovalJob);
        jobMap.put("documentReportJob",   batchJobLauncher::documentReportJob);
    }

    /**
     * BatchApplication 의 시작 시 정리(cleanupOnStartup)가 끝난 뒤 딱 한 번 호출한다.
     * 이 시점부터 스케줄 등록이 시작된다.
     */
    public synchronized void start() {
        ready = true;
        log.info("[DynamicScheduler] 시작 정리 완료 → 스케줄 등록 시작");
        reloadSchedules();
    }

    // 1분마다 DB 폴링 → 크론이 바뀐 Job 만 재등록 (start() 호출 전에는 아무것도 하지 않음)
    @Scheduled(fixedDelay = 60_000)
    public synchronized void reloadSchedules() {
        if (!ready) {
            return;
        }

        List<BatchScheduleVo> schedules;
        try {
            schedules = scheduleMapper.selectActiveSchedules();
        } catch (Exception e) {
            log.error("[DynamicScheduler] 스케줄 조회 실패 (기존 스케줄 유지)", e);
            return;   // 조회 실패 시에는 취소 처리도 하지 않는다
        }

        Set<String> activeJobNames = new HashSet<>();
        for (BatchScheduleVo schedule : schedules) {
            activeJobNames.add(schedule.getJobName());
            try {
                register(schedule);
            } catch (Exception e) {
                log.error("[DynamicScheduler] 스케줄 등록 실패: {}", schedule.getJobName(), e);
            }
        }

        // 비활성(USE_YN='N') 또는 삭제된 스케줄 → 등록돼 있던 태스크 취소
        for (String jobName : new ArrayList<>(scheduledTasks.keySet())) {
            if (!activeJobNames.contains(jobName)) {
                ScheduledFuture<?> task = scheduledTasks.remove(jobName);
                registeredCrons.remove(jobName);
                if (task != null) {
                    task.cancel(false);   // 실행 중인 Job 은 끝까지 두고 다음 실행부터 막는다
                }
                log.info("[DynamicScheduler] 비활성/삭제로 취소: {}", jobName);
            }
        }
    }

    private void register(BatchScheduleVo schedule) {
        String   jobName  = schedule.getJobName();
        String   cronExpr = schedule.getCronExpr() == null ? null : schedule.getCronExpr().trim();
        Runnable runnable = jobMap.get(jobName);

        if (runnable == null) {
            if (warnedJobs.add(jobName)) {
                log.warn("[DynamicScheduler] jobMap 미등록 Job (USE_YN='N' 처리 또는 jobMap 등록 필요): {}", jobName);
            }
            return;
        }

        // 크론 검증 → 잘못된 값이면 기존 스케줄을 그대로 유지하고 건너뜀
        if (!isValidCron(cronExpr)) {
            if (!String.valueOf(cronExpr).equals(invalidCrons.get(jobName))) {
                invalidCrons.put(jobName, String.valueOf(cronExpr));
                log.error("[DynamicScheduler] 잘못된 크론 → 기존 스케줄 유지: {} → {}", jobName, cronExpr);
            }
            return;
        }
        invalidCrons.remove(jobName);

        ScheduledFuture<?> currentTask = scheduledTasks.get(jobName);
        boolean alive = currentTask != null && !currentTask.isCancelled();

        // 크론 동일 + 태스크 살아있으면 SKIP
        if (alive && cronExpr.equals(registeredCrons.get(jobName))) {
            log.debug("[DynamicScheduler] 변경 없음 SKIP: {} → {}", jobName, cronExpr);
            return;
        }

        // 크론이 바뀌었거나 태스크가 없으면 → 기존 취소 후 재등록
        if (alive) {
            currentTask.cancel(false);
            log.info("[DynamicScheduler] 취소: {} ({})", jobName, registeredCrons.get(jobName));
        }

        ScheduledFuture<?> future = taskScheduler.schedule(runnable, new CronTrigger(cronExpr, ZONE));
        scheduledTasks.put(jobName, future);
        registeredCrons.put(jobName, cronExpr);
        log.info("[DynamicScheduler] 등록: {} → {}", jobName, cronExpr);
    }

    /** 크론 표현식 검증: CronExpression.parse 가 실패하면 IllegalArgumentException */
    private static boolean isValidCron(String cronExpr) {
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return false;
        }
        try {
            CronExpression.parse(cronExpr);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}