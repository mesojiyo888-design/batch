package doc.batch.controller;

import doc.batch.controller.service.BatchLaunchService;
import doc.batch.controller.service.BatchRunHistVo;
import doc.batch.controller.service.BatchScheduleVo;
import doc.batch.controller.service.mapper.BatchScheduleMapper;
import doc.batch.scheduler.DynamicScheduler;
import org.springframework.batch.core.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
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
 *   GET  /api/batch/schedules                  스케줄 목록
 *   PUT  /api/batch/schedules/{jobName}        크론 표현식 변경
 *   PUT  /api/batch/schedules/{jobName}/toggle 사용여부 토글
 *
 * [실행 이력]
 *   GET  /api/batch/jobs/{jobName}/history
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
            result.put("success", false);
            result.put("message", "Job 실행 실패. 로그를 확인하세요.");
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
            result.put("success", false);
            result.put("message", "Step 실행 실패. 로그를 확인하세요.");
        }
        return ResponseEntity.ok(result);
    }

    // ───────────────────────────────────────────────────────────────
    // 스케줄 목록 조회
    // GET /api/batch/schedules
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/schedules")
    public ResponseEntity<List<BatchScheduleVo>> getSchedules() {
        return ResponseEntity.ok(scheduleMapper.selectActiveSchedules());
    }

    // ───────────────────────────────────────────────────────────────
    // 크론 표현식 변경 + 즉시 반영
    // PUT /api/batch/schedules/documentReportJob
    // Body: { "cronExpr": "0 0 8 * * MON" }
    // ───────────────────────────────────────────────────────────────
    @PutMapping("/schedules/{jobName}")
    public ResponseEntity<Map<String, Object>> updateSchedule(
            @PathVariable String jobName,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String cronExpr = body.get("cronExpr");
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "cronExpr 필수");
            return ResponseEntity.badRequest().body(err);
        }

        String userId = (String) request.getAttribute("loginUserId");

        BatchScheduleVo vo = new BatchScheduleVo();
        vo.setJobName(jobName);
        vo.setCronExpr(cronExpr.trim());
        // vo.setModifiedBy(userId);   // VO에 modifiedBy 추가 시 활성화
        scheduleMapper.updateCronExpr(vo);

        // 스케줄 즉시 재등록 (1분 폴링 기다리지 않음)
        dynamicScheduler.reloadSchedules();

        Map<String, Object> result = new HashMap<>();
        result.put("success",  true);
        result.put("jobName",  jobName);
        result.put("cronExpr", cronExpr);
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
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "Job을 찾을 수 없습니다: " + jobName);
            return ResponseEntity.badRequest().body(err);
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
    // 실행 이력 조회
    // GET /api/batch/jobs/documentReportJob/history
    // ───────────────────────────────────────────────────────────────
    @GetMapping("/jobs/{jobName}/history")
    public ResponseEntity<List<BatchRunHistVo>> getHistory(@PathVariable String jobName) {
        return ResponseEntity.ok(scheduleMapper.selectRunHistList(jobName));
    }
}
