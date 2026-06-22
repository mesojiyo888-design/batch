package doc.batch.scheduler;


import doc.batch.controller.service.BatchLaunchService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;

/**
 * Job별 실행 메서드를 모아둔 컴포넌트.
 *
 * @SchedulerLock은 Spring AOP 프록시 기반이라
 * 람다 안에서는 동작하지 않음 → 반드시 별도 Bean의 public 메서드에 선언해야 함.
 *
 */
@Component
public class BatchJobLauncher {

    private final BatchLaunchService launchService;

    public BatchJobLauncher(BatchLaunchService launchService) {
        this.launchService = launchService;
    }

    // Job 추가 시 여기에 메서드 추가
    // lockAtMostFor: 서버 다운 시 자동 해제 시간 (Job 예상 소요 시간보다 넉넉하게)
    // lockAtLeastFor: 너무 빨리 끝나도 중복 방지용 최소 잠금 시간

    @SchedulerLock(name = "documentSyncJob", lockAtLeastFor = "5m", lockAtMostFor = "30m")
    public void documentSyncJob() {
        launchService.launch("documentSyncJob", "scheduler", "AUTO");
    }

    @SchedulerLock(name = "documentApprovalJob", lockAtLeastFor = "5m", lockAtMostFor = "30m")
    public void documentApprovalJob() {
        launchService.launch("documentApprovalJob", "scheduler", "AUTO");
    }

    @SchedulerLock(name = "documentReportJob", lockAtLeastFor = "5m", lockAtMostFor = "1h")
    public void documentReportJob() {
        launchService.launch("documentReportJob", "scheduler", "AUTO");
    }
}