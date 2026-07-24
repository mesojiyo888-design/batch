package doc.batch.job;

import doc.batch.controller.service.BatchScheduleVo;
import doc.batch.controller.service.mapper.BatchScheduleMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;

import javax.sql.DataSource;

/**
 * genericJob - 완전 동적 Job 등록 기능
 *
 * BATCH_SCHEDULE 테이블에 신규 JOB_NAME(TASKLET_TYPE != 'NATIVE')을 등록하면
 * 코드 배포 없이 이 하나의 Job/Tasklet으로 실행된다.
 * 실행할 잡 이름은 JobParameter "targetJobName" 으로 런타임에 전달받는다.
 *
 * ⚠ 관리자 전용 기능 (Admin-only)
 *   TASKLET_TYPE=SQL 인 경우 TASKLET_CONFIG에 저장된 SQL문을 그대로 실행하므로
 *   SQL Injection / 임의 SQL 실행 위험이 있다. 현재 별도 인증/권한 체크는 구현되어
 *   있지 않으므로, 스케줄 등록/수정 API(BatchController)는 반드시 관리자만 접근
 *   가능하도록 인프라(WAF, 게이트웨이 인증 등) 단에서 통제해야 한다.
 */
@Configuration
public class GenericJobConfig {

    private static final Logger log = LoggerFactory.getLogger(GenericJobConfig.class);

    /** 실행 대상 Job 이름을 담는 JobParameter 키 */
    public static final String PARAM_TARGET_JOB_NAME = "targetJobName";

    private final JobBuilderFactory  jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;

    public GenericJobConfig(JobBuilderFactory jobBuilderFactory,
                             StepBuilderFactory stepBuilderFactory) {
        this.jobBuilderFactory  = jobBuilderFactory;
        this.stepBuilderFactory = stepBuilderFactory;
    }

    // ---------------------------------------------------------------
    // Job 정의 - Bean 이름 "genericJob"
    // ---------------------------------------------------------------
    @Bean
    public Job genericJob(BatchScheduleMapper scheduleMapper, DataSource dataSource) {
        return jobBuilderFactory.get("genericJob")
                .incrementer(new RunIdIncrementer())
                .start(genericStep(scheduleMapper, dataSource))
                .build();
    }

    // ---------------------------------------------------------------
    // Step 정의 - 단일 Tasklet, DB 설정 기반 동적 실행
    // ---------------------------------------------------------------
    @Bean
    public Step genericStep(BatchScheduleMapper scheduleMapper, DataSource dataSource) {
        return stepBuilderFactory.get("genericStep")
                .tasklet((contribution, chunkContext) -> {

                    String targetJobName = (String) chunkContext.getStepContext()
                            .getJobParameters().get(PARAM_TARGET_JOB_NAME);

                    if (targetJobName == null || targetJobName.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                                "genericJob 실행에는 JobParameter '" + PARAM_TARGET_JOB_NAME + "'가 필수입니다.");
                    }

                    BatchScheduleVo schedule = scheduleMapper.selectSchedule(targetJobName);
                    if (schedule == null) {
                        throw new IllegalStateException(
                                "BATCH_SCHEDULE에 등록되지 않은 Job입니다: " + targetJobName);
                    }
                    if (schedule.isNative()) {
                        throw new IllegalStateException(
                                "NATIVE 타입 Job은 genericJob으로 실행할 수 없습니다: " + targetJobName);
                    }

                    String taskletType   = schedule.getTaskletType();
                    String taskletConfig = schedule.getTaskletConfig();

                    log.info("[genericJob] {} 시작 - taskletType={}", targetJobName, taskletType);

                    if (taskletConfig == null || taskletConfig.trim().isEmpty()) {
                        throw new IllegalStateException(
                                "TASKLET_CONFIG가 비어있습니다: " + targetJobName);
                    }

                    JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

                    if ("SQL".equalsIgnoreCase(taskletType)) {
                        // ⚠ 관리자 전용 - TASKLET_CONFIG 원문을 그대로 실행 (SQL Injection 위험)
                        int updateCount = jdbcTemplate.update(taskletConfig);
                        log.info("[genericJob][{}] SQL 실행 완료 - 영향받은 행 수: {}", targetJobName, updateCount);

                    } else if ("PROCEDURE".equalsIgnoreCase(taskletType)) {
                        // TASKLET_CONFIG 형식:
                        //   - 단독 프로시저: "PROC_SYNC_DOC"
                        //   - 패키지 소속 프로시저: "PKG_BATCH.PROC_SYNC_DOC" (패키지명.프로시저명)
                        // ⚠ 파라미터 없는(IN/OUT 인자 없는) 프로시저만 지원합니다. 파라미터가 있는
                        //   프로시저는 이 방식으로 호출할 수 없습니다(향후 확장 필요).
                        // ⚠ 관리자 전용 - 임의 프로시저 호출 위험
                        String procConfig = taskletConfig.trim();
                        SimpleJdbcCall call = new SimpleJdbcCall(jdbcTemplate);

                        int dotIdx = procConfig.indexOf('.');
                        if (dotIdx > 0) {
                            // 패키지.프로시저 형태 - Oracle 패키지 프로시저는 catalogName(패키지명)을
                            // procedureName과 별도로 지정해야 SimpleJdbcCall이 정확히 인식한다.
                            String packageName   = procConfig.substring(0, dotIdx).trim();
                            String procedureName = procConfig.substring(dotIdx + 1).trim();
                            call.withCatalogName(packageName).withProcedureName(procedureName);
                        } else {
                            call.withProcedureName(procConfig);
                        }

                        call.execute();
                        log.info("[genericJob][{}] PROCEDURE 호출 완료: {}", targetJobName, taskletConfig);

                    } else {
                        // API 등 미구현 타입 - Step 실패 처리
                        throw new UnsupportedOperationException(
                                "지원하지 않는 TASKLET_TYPE입니다 (미구현): " + taskletType
                                        + " (jobName=" + targetJobName + ")");
                    }

                    log.info("[genericJob] {} 완료", targetJobName);
                    return RepeatStatus.FINISHED;
                })
                .build();
    }
}
