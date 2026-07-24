package doc.batch.controller;

import doc.batch.controller.service.BatchLaunchService;
import doc.batch.controller.service.BatchRunHistVo;
import doc.batch.controller.service.BatchScheduleVo;
import doc.batch.controller.service.mapper.BatchScheduleMapper;
import doc.batch.scheduler.DynamicScheduler;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 배치 관리 REST API
 *
 * [수동 실행]
 *   POST /api/batch/jobs/{jobName}/run
 *   POST /api/batch/jobs/{jobName}/steps/{stepName}/run   ← 특정 Step만 실행
 *
 * [스케줄 관리]
 *   GET    /api/batch/schedules?pageIndex=&pageSize=       스케줄 목록 (활성만, 페이징)
 *   GET    /api/batch/schedules/all?pageIndex=&pageSize=   스케줄 전체 목록 (비활성 포함, 페이징)
 *   POST   /api/batch/schedules                   신규 스케줄 등록 (동적 Job 등록)
 *   PUT    /api/batch/schedules/{jobName}          스케줄 전체 수정 (크론 + 동적 Job 설정)
 *   DELETE /api/batch/schedules/{jobName}          스케줄 삭제 (NATIVE 포함 전부 삭제 가능.
 *                                                   NATIVE는 배포된 자바 Bean은 남고 스케줄만 사라짐)
 *   PUT    /api/batch/schedules/{jobName}/toggle   사용여부 토글
 *   PUT    /api/batch/schedules/toggle-bulk        사용여부 일괄 변경
 *
 * [실행 이력]
 *   GET  /api/batch/jobs/{jobName}/history?startDt=&endDt=&pageIndex=&pageSize=
 *   GET  /api/batch/history?startDt=&endDt=&pageIndex=&pageSize=   전체 Job 이력 검색
 *
 * ※ 목록/이력 조회 API는 pageIndex(기본 1)/pageSize를 받아 { dataList, totalCount } 형태로 응답한다.
 *   pageSize는 화면(ToastGrid pageOptions.perPage)이 결정해 넘기므로, 추후 사용자별 페이지당
 *   건수 설정이 추가돼도 이 API는 그대로 재사용된다.
 *
 * ※ 동적 Job 등록/수정 API는 TASKLET_CONFIG에 임의 SQL/프로시저를 저장해 실행하므로
 *   관리자(Admin) 전용 기능이다. 현재 별도 인증/권한 체크는 구현되어 있지 않으며,
 *   운영 반영 전 반드시 접근 통제(인증 미들웨어, 게이트웨이 등)를 추가해야 한다.
 */
@RestController
@RequestMapping("/api/batch")
public class BatchController {

    private final BatchLaunchService launchService;
    private final BatchScheduleMapper scheduleMapper;
    private final DynamicScheduler dynamicScheduler;

    public BatchController(BatchLaunchService launchService,
                            BatchScheduleMapper scheduleMapper,
                            DynamicScheduler dynamicScheduler) {
        this.launchService    = launchService;
        this.scheduleMapper   = scheduleMapper;
        this.dynamicScheduler = dynamicScheduler;
    }

    // ───────────────────────────────────────────────────────────────
    // 수동 실행 - Job 전체
    //
    // POST /api/batch/jobs/documentSyncJob/run
    // POST /api/batch/jobs/documentApprovalJob/run
    // POST /api/batch/jobs/documentReportJob/run
    // ───────────────────────────────────────────────────────────────
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<Map<String, Object>> runJob(
            @PathVariable String jobName,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute("loginUserId");
        if (userId == null) userId = "unknown";

        JobExecution execution = launchService.launch(jobName, userId, "MANUAL");

        Map<String, Object> result = new HashMap<>();
        if (execution != null) {
            result.put("success",     true);
            result.put("executionId", execution.getId());
            result.put("status",      execution.getStatus().name());
        } else {
            result = errorBody("Job 실행 실패. 로그를 확인하세요.");
        }
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 수동 실행 - 특정 Step만
    //
    // POST /api/batch/jobs/documentReportJob/steps/reportStep1/run
    // POST /api/batch/jobs/documentReportJob/steps/reportStep2/run
    // POST /api/batch/jobs/documentReportJob/steps/reportStep3/run
    //
    // ※ documentSyncJob(단일 Tasklet), documentApprovalJob(순차 의존)은
    //   Step 단독 실행이 의미 없으므로 이 API는 documentReportJob 전용으로 사용
    // ───────────────────────────────────────────────────────────────
    @PostMapping("/jobs/{jobName}/steps/{stepName}/run")
    public ResponseEntity<Map<String, Object>> runStep(
            @PathVariable String jobName,
            @PathVariable String stepName,
            HttpServletRequest request) {

        String userId = (String) request.getAttribute("loginUserId");
        if (userId == null) userId = "unknown";

        JobExecution execution = launchService.launch(jobName, stepName, userId, "MANUAL");

        Map<String, Object> result = new HashMap<>();
        if (execution != null) {
            result.put("success",     true);
            result.put("executionId", execution.getId());
            result.put("status",      execution.getStatus().name());
            result.put("targetStep",  stepName);
        } else {
            result = errorBody("Step 실행 실패. 로그를 확인하세요.");
        }
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 목록 조회 (페이징)
    // GET /api/batch/schedules?pageIndex=1&pageSize=20
    // 응답: { dataList: [...], totalCount: N }
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/schedules")
    public ResponseEntity<Map<String, Object>> getSchedules(
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "20") int pageSize) {

        Map<String, Object> params = pageParams(pageIndex, pageSize);
        List<BatchScheduleVo> list = scheduleMapper.selectActiveSchedules(params);
        int totalCount = scheduleMapper.selectActiveSchedulesCount();

        return ResponseEntity.ok(pageResult(list, totalCount));
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 전체 목록 (비활성 포함, 페이징)
    // GET /api/batch/schedules/all?pageIndex=1&pageSize=20
    // 응답: { dataList: [...], totalCount: N }
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/schedules/all")
    public ResponseEntity<Map<String, Object>> getAllSchedules(
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "20") int pageSize) {

        Map<String, Object> params = pageParams(pageIndex, pageSize);
        List<BatchScheduleVo> list = scheduleMapper.selectAllSchedules(params);
        int totalCount = scheduleMapper.selectAllSchedulesCount();

        return ResponseEntity.ok(pageResult(list, totalCount));
    }

    // ───────────────────────────────────────────────────────────────
    // 신규 스케줄(동적 Job) 등록
    // POST /api/batch/schedules
    // Body: { "jobName": "myNewJob", "cronExpr": "0 0 3 * * *",
    //         "taskletType": "SQL", "taskletConfig": "UPDATE ...",
    //         "description": "설명", "useYn": "N" }
    //
    // ⚠ 관리자 전용 - taskletConfig는 임의 SQL/프로시저를 저장하므로 인증 필요
    // ───────────────────────────────────────────────────────────────
    @PostMapping("/schedules")
    public ResponseEntity<Map<String, Object>> createSchedule(
            @RequestBody BatchScheduleVo vo,
            HttpServletRequest request) {

        Map<String, Object> err = validateScheduleInput(vo);
        if (err != null) {
            return ResponseEntity.badRequest().body(err);
        }

        if (scheduleMapper.selectSchedule(vo.getJobName()) != null) {
            return ResponseEntity.badRequest().body(errorBody("이미 등록된 jobName 입니다: " + vo.getJobName()));
        }

        String userId = (String) request.getAttribute("loginUserId");

        vo.setCronExpr(vo.getCronExpr().trim());
        vo.setUseYn(vo.getUseYn() == null || vo.getUseYn().trim().isEmpty() ? "N" : vo.getUseYn());
        vo.setModifiedBy(userId);
        scheduleMapper.insertSchedule(vo);

        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("jobName", vo.getJobName());
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 전체 수정 (크론 + 동적 Job 설정) + 즉시 반영
    // PUT /api/batch/schedules/documentReportJob
    // Body: { "cronExpr": "0 0 8 * * MON", "taskletType": "SQL",
    //         "taskletConfig": "...", "description": "..." }
    // ───────────────────────────────────────────────────────────────
    @PutMapping("/schedules/{jobName}")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable String jobName,
            @RequestBody BatchScheduleVo body,
            HttpServletRequest request) {

        if (scheduleMapper.selectSchedule(jobName) == null) {
            return ResponseEntity.badRequest().body(errorBody("Job을 찾을 수 없습니다: " + jobName));
        }

        String cronExpr = body.getCronExpr();
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("cronExpr 필수"));
        }
        if (!CronExpression.isValidExpression(cronExpr.trim())) {
            return ResponseEntity.badRequest().body(errorBody("cronExpr 형식이 올바르지 않습니다: " + cronExpr));
        }

        String userId = (String) request.getAttribute("loginUserId");

        BatchScheduleVo vo = new BatchScheduleVo();
        vo.setJobName(jobName);
        vo.setCronExpr(cronExpr.trim());
        vo.setDescription(body.getDescription());
        vo.setTaskletType(body.getTaskletType());
        vo.setTaskletConfig(body.getTaskletConfig());
        vo.setModifiedBy(userId);
        scheduleMapper.updateSchedule(vo);

        // 스케줄 즉시 재등록 (1분 폴링 기다리지 않음)
        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success",  true);
        result.put("jobName",  jobName);
        result.put("cronExpr", cronExpr);
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 삭제
    // DELETE /api/batch/schedules/myNewJob
    //
    // ※ NATIVE 타입 삭제 제한은 임시로 해제한 상태. NATIVE는 실제 자바 @Bean(Job 클래스)이
    //   코드로 이미 배포돼 있는 상태라, BATCH_SCHEDULE 행만 지워도 자바 빈 자체는 그대로 남는다.
    //   즉 삭제해도 실제 Job이 없어지는 게 아니라 스케줄(크론 등록)만 사라지므로,
    //   운영 반영 전 다시 막을지(또는 삭제 시 경고 문구를 추가할지) 결정 필요.
    // ───────────────────────────────────────────────────────────────
    @DeleteMapping("/schedules/{jobName}")
    public ResponseEntity<Map<String, Object>> deleteSchedule(@PathVariable String jobName) {
        BatchScheduleVo current = scheduleMapper.selectSchedule(jobName);
        if (current == null) {
            return ResponseEntity.badRequest().body(errorBody("Job을 찾을 수 없습니다: " + jobName));
        }

        scheduleMapper.deleteSchedule(jobName);
        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("jobName", jobName);
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 사용여부 토글 (Y ↔ N)
    // PUT /api/batch/schedules/documentReportJob/toggle
    // ───────────────────────────────────────────────────────────────
    @PutMapping("/schedules/{jobName}/toggle")
    public ResponseEntity<Map<String, Object>> toggleSchedule(@PathVariable String jobName) {
        BatchScheduleVo current = scheduleMapper.selectSchedule(jobName);
        if (current == null) {
            return ResponseEntity.badRequest().body(errorBody("Job을 찾을 수 없습니다: " + jobName));
        }

        BatchScheduleVo vo = new BatchScheduleVo();
        vo.setJobName(jobName);
        vo.setUseYn("Y".equals(current.getUseYn()) ? "N" : "Y");
        scheduleMapper.updateUseYn(vo);

        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("jobName", jobName);
        result.put("useYn",   vo.getUseYn());
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 사용여부 일괄 변경
    // PUT /api/batch/schedules/toggle-bulk
    // Body: [ { "jobName": "documentSyncJob", "useYn": "N" }, ... ]
    // ───────────────────────────────────────────────────────────────
    @PutMapping("/schedules/toggle-bulk")
    public ResponseEntity<Map<String, Object>> toggleBulk(@RequestBody List<BatchScheduleVo> items) {
        if (items == null || items.isEmpty()) {
            return ResponseEntity.badRequest().body(errorBody("변경할 항목이 없습니다."));
        }

        List<String> updated = new ArrayList<>();
        for (BatchScheduleVo item : items) {
            if (item.getJobName() == null || item.getUseYn() == null) {
                continue;
            }
            BatchScheduleVo vo = new BatchScheduleVo();
            vo.setJobName(item.getJobName());
            vo.setUseYn(item.getUseYn());
            scheduleMapper.updateUseYn(vo);
            updated.add(item.getJobName());
        }

        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("updated", updated);
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 실행 이력 조회 (특정 Job, 기간/상태 검색 옵션 + 페이징)
    // GET /api/batch/jobs/documentReportJob/history?startDt=&endDt=&status=&pageIndex=1&pageSize=10
    // 응답: { dataList: [...], totalCount: N }
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/jobs/{jobName}/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable String jobName,
            @RequestParam(required = false) String startDt,
            @RequestParam(required = false) String endDt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "10") int pageSize) {

        Map<String, Object> params = pageParams(pageIndex, pageSize);
        params.put("jobName", jobName);
        params.put("startDt", startDt);
        params.put("endDt",   endDt);
        params.put("status",  status);

        List<BatchRunHistVo> list = scheduleMapper.selectRunHistList(params);
        int totalCount = scheduleMapper.selectRunHistListCount(params);

        return ResponseEntity.ok(pageResult(list, totalCount));
    }

    // ───────────────────────────────────────────────────────────────
    // 전체 Job 실행 이력 조회 (jobName/기간/상태 검색 옵션 + 페이징)
    // GET /api/batch/history?jobName=&startDt=&endDt=&status=&pageIndex=1&pageSize=10
    // 응답: { dataList: [...], totalCount: N }
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getAllHistory(
            @RequestParam(required = false) String jobName,
            @RequestParam(required = false) String startDt,
            @RequestParam(required = false) String endDt,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageIndex,
            @RequestParam(defaultValue = "10") int pageSize) {

        Map<String, Object> params = pageParams(pageIndex, pageSize);
        params.put("jobName", jobName);
        params.put("startDt", startDt);
        params.put("endDt",   endDt);
        params.put("status",  status);

        List<BatchRunHistVo> list = scheduleMapper.selectRunHistList(params);
        int totalCount = scheduleMapper.selectRunHistListCount(params);

        return ResponseEntity.ok(pageResult(list, totalCount));
    }

    // ───────────────────────────────────────────────────────────────
    // 페이징 공통 유틸
    // - offset: (pageIndex - 1) * pageSize (0 미만/0 이하 값 방어)
    // ───────────────────────────────────────────────────────────────
    private Map<String, Object> pageParams(int pageIndex, int pageSize) {
        int safePageIndex = Math.max(pageIndex, 1);
        int safePageSize  = Math.max(pageSize, 1);

        Map<String, Object> params = new HashMap<>();
        params.put("offset",   (safePageIndex - 1) * safePageSize);
        params.put("pageSize", safePageSize);
        return params;
    }

    private Map<String, Object> pageResult(List<?> dataList, int totalCount) {
        Map<String, Object> result = new HashMap<>();
        result.put("dataList",   dataList);
        result.put("totalCount", totalCount);
        return result;
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 등록/수정 공통 입력 검증
    // - jobName, cronExpr 필수
    // - cronExpr은 Spring의 CronExpression 문법으로 유효성 검사
    // ───────────────────────────────────────────────────────────────
    private Map<String, Object> validateScheduleInput(BatchScheduleVo vo) {
        if (vo.getJobName() == null || vo.getJobName().trim().isEmpty()) {
            return errorBody("jobName 필수");
        }
        if (vo.getCronExpr() == null || vo.getCronExpr().trim().isEmpty()) {
            return errorBody("cronExpr 필수");
        }
        if (!CronExpression.isValidExpression(vo.getCronExpr().trim())) {
            return errorBody("cronExpr 형식이 올바르지 않습니다: " + vo.getCronExpr());
        }
        return null;
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("message", message);
        return err;
    }
}
