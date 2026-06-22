package doc.batch.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * documentSyncJob - 문서 동기화 배치
 * 
 * 단일 Tasklet 방식: Step 분리 없이 하나의 Tasklet에서 전체 처리.
 * 간단한 작업이거나 트랜잭션 단위 분리가 불필요할 때 사용.
 */
@Configuration
public class DocumentSyncJobConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentSyncJobConfig.class);

    private final JobBuilderFactory  jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    // 실제 프로젝트에서는 Service 주입받아 사용
    // private final DocumentSyncService syncService;

    public DocumentSyncJobConfig(JobBuilderFactory jobBuilderFactory,
                                  StepBuilderFactory stepBuilderFactory) {
        this.jobBuilderFactory  = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
    }

    // ---------------------------------------------------------------
    // Job 정의
    // Bean 이름 = "documentSyncJob" → ApplicationContext.getBean()으로 조회
    // ---------------------------------------------------------------
    @Bean
    public Job documentSyncJob() {
        return jobBuilderFactory.get("documentSyncJob")
                .incrementer(new RunIdIncrementer())
                .start(documentSyncStep())
                .build();
    }

    // ---------------------------------------------------------------
    // Step 정의 - 단일 Tasklet
    // ---------------------------------------------------------------
    @Bean
    public Step documentSyncStep() {
        return stepBuilderFactory.get("documentSyncStep")
                .tasklet((contribution, chunkContext) -> {

                    String triggeredBy = (String) chunkContext.getStepContext()
                            .getJobParameters().get("triggeredBy");
                    log.info("[documentSyncJob] 시작 - 실행자: {}", triggeredBy);

                    // ── 실제 로직 ──────────────────────────────────────
                    // 1. 외부 시스템 or 임시 테이블에서 문서 목록 조회
                    // List<DocumentVo> docs = syncService.fetchPendingDocs();
                    //
                    // 2. 본 테이블 upsert
                    // syncService.upsertDocuments(docs);
                    //
                    // 3. 처리 완료 표시
                    // syncService.markSynced(docs);
                    // ──────────────────────────────────────────────────

                    log.info("[documentSyncJob] 완료");
                    return RepeatStatus.FINISHED;
                })
                .build();
    }
}
