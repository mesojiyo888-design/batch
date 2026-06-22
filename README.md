# 배치 API 사용 가이드

## Job 목록

| Job Bean 이름         | 방식              | 설명             |
|-----------------------|-------------------|------------------|
| documentSyncJob       | 단일 Tasklet      | 문서 동기화       |
| documentApprovalJob   | Step 1→2→3 순차   | 결재 마감 처리    |
| documentReportJob     | Step 1→2→3 (선택) | 보고서 생성       |

---

## 수동 실행 API

### Job 전체 실행
```
POST /api/batch/jobs/{jobName}/run
```

예시:
```bash
# 문서 동기화 전체 실행
curl -X POST http://localhost:8080/api/batch/jobs/documentSyncJob/run

# 결재 마감 전체 실행
curl -X POST http://localhost:8080/api/batch/jobs/documentApprovalJob/run

# 보고서 전체 실행 (Step1 → Step2 → Step3)
curl -X POST http://localhost:8080/api/batch/jobs/documentReportJob/run
```

응답:
```json
{
  "success": true,
  "executionId": 42,
  "status": "COMPLETED"
}
```

---

### 특정 Step만 실행 (documentReportJob 전용)
```
POST /api/batch/jobs/{jobName}/steps/{stepName}/run
```

예시:
```bash
# Step1만 실행 (데이터 집계만)
curl -X POST http://localhost:8080/api/batch/jobs/documentReportJob/steps/reportStep1/run

# Step2만 실행 (파일 생성만)
curl -X POST http://localhost:8080/api/batch/jobs/documentReportJob/steps/reportStep2/run

# Step3만 실행 (배포만)
curl -X POST http://localhost:8080/api/batch/jobs/documentReportJob/steps/reportStep3/run
```

응답:
```json
{
  "success": true,
  "executionId": 43,
  "status": "COMPLETED",
  "targetStep": "reportStep2"
}
```

---

## 스케줄 관리 API

### 스케줄 목록 조회
```bash
GET /api/batch/schedules
```

### 크론 표현식 변경 (즉시 반영)
```bash
PUT /api/batch/schedules/documentReportJob
Content-Type: application/json

{ "cronExpr": "0 0 8 * * MON" }
```

### 스케줄 활성/비활성 토글
```bash
PUT /api/batch/schedules/documentReportJob/toggle
```

---

## 실행 이력 조회
```bash
GET /api/batch/jobs/documentReportJob/history
```

---

## 크론 표현식 예시

| 표현식              | 설명              |
|---------------------|-------------------|
| 0 0 2 * * *         | 매일 02:00        |
| 0 30 1 * * *        | 매일 01:30        |
| 0 0 6 * * MON       | 매주 월요일 06:00 |
| 0 0/30 * * * *      | 30분마다          |
| 0 0 9-18 * * MON-FRI| 평일 9시~18시 매시|

---

## 특정 Step만 실행하는 원리

`documentReportJob`의 각 Step은 `skipAwareTasklet` 래퍼를 사용.

JobParameter `targetStep` 값을 확인해서:
- `targetStep` 없음 / `"ALL"` → 모든 Step 정상 실행
- `targetStep = "reportStep2"` → Step1, Step3은 로직 SKIP (FINISHED 반환)
  → Step2만 실제 실행

Spring Batch JobRepository에는 모든 Step이 COMPLETED로 기록됨.
실제 SKIP된 Step은 로그에서 확인 가능 (`[Step1] targetStep=reportStep2 → SKIP`).
