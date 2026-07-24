package doc.batch.scheduler;


import doc.batch.controller.service.BatchLaunchService;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 스케줄(크론) 기반 Job 실행 공통 트리거.
 *
 * NATIVE(자바 클래스로 구현된 Job)든 SQL/PROCEDURE(동적 등록 Job)든
 * jobName만 넘기면 이 메서드 하나로 실행된다. 실제 Job 해석(Bean 이름 조회 vs
 * genericJob 위임)은 BatchLaunchService가 담당하므로, 신규 Job이 추가돼도
 * 이 클래스는 더 이상 수정할 필요가 없다.
 *
 * ※ 예전에는 Job마다 @SchedulerLock이 붙은 전용 메서드가 필요했다(name 속성이
 *   컴파일 타임 고정값만 허용해서 동적 jobName을 못 받았기 때문). 지금은
 *   LockProvider를 코드로 직접 사용해 jobName 기반의 락을 걸기 때문에
 *   Job이 몇 개든, 어떤 타입이든 이 메서드 하나로 충분하다.
 */
@Component
public class BatchJobLauncher {

    private static final Logger log = LoggerFactory.getLogger(BatchJobLauncher.class);

    private final BatchLaunchService launchService;
    private final LockProvider       lockProvider;

    public BatchJobLauncher(BatchLaunchService launchService, LockProvider lockProvider) {
        this.launchService = launchService;
        this.lockProvider  = lockProvider;
    }

    // ─────────────────────────────────────────────────────────────
    // 모든 Job(NATIVE + SQL/PROCEDURE) 공통 스케줄 트리거.
    //
    // BATCH_SCHEDULE에 등록된 Job은 타입에 관계없이 이 메서드 하나로
    // 스케줄링된다. 새 Job이 추가돼도 이 클래스에 코드를 더할 필요가 없다.
    //
    // lockAtMostFor: 서버 다운 시 자동 해제 시간 (Job 예상 소요 시간보다 넉넉하게)
    // lockAtLeastFor: 너무 빨리 끝나도 중복 방지용 최소 잠금 시간
    // ─────────────────────────────────────────────────────────────
    public void runScheduledJob(String jobName) {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(), jobName, Duration.ofHours(1), Duration.ofMinutes(1));

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig);
        if (!lock.isPresent()) {
            log.info("[BatchJobLauncher] {} 락 획득 실패 - 다른 인스턴스에서 실행 중 → SKIP", jobName);
            return;
        }

        try {
            launchService.launch(jobName, "scheduler", "AUTO");
        } finally {
            lock.get().unlock();
        }
    }
}