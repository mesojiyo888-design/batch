# 배치 API 사용 가이드

## Job 목록

| Job Bean 이름         | 방식              | 설명             |
|-----------------------|-------------------|------------------|
| documentSyncJob       | 단일 Tasklet      | 문서 동기화       |
| documentApprovalJob   | Step 1→2→3 순차   | 결재 마감 처리    |
| documentReportJob     | Step 1→2→3 (선택) | 보고서 생성       |

이 외에 `BATCH_SCHEDULE`에 등록만 하면 코드 배포 없이 실행되는 Job도 추가할 수 있습니다. 아래
"Job 타입" 및 "스케줄 등록/수정/삭제 API" 참고.

---

## Job 타입 (TASKLET_TYPE)

배치를 등록할 때 아래 세 가지 타입 중 하나를 지정합니다.

| 타입 | 설정값(TASKLET_CONFIG)에 넣는 것 | 사전 준비 | 코드 배포 필요 여부 |
|------|-----------------------------------|-----------|----------------------|
| `NATIVE` | 사용 안 함(비워둠) | 자바로 Job Bean을 작성하고, Bean 이름을 등록할 jobName과 동일하게 배포 | 최초 1회 필요 |
| `SQL` | 실행할 SQL문 원문 (INSERT/UPDATE/DELETE 등. SELECT 미지원) | 없음 (SQL 안에서 참조하는 테이블/컬럼만 존재하면 됨) | 불필요 |
| `PROCEDURE` | 호출할 프로시저 이름. 단독이면 `PROC_NAME`, 패키지 소속이면 `PKG_NAME.PROC_NAME` | DB에 프로시저를 미리 `CREATE PROCEDURE`로 생성해둬야 함 (Oracle 전용) | 불필요 |

`SQL` 타입은 등록된 SQL문을 그대로 실행하므로 관리자 전용으로 접근을 통제해야 합니다.

⚠️ `PROCEDURE` 타입은 **파라미터(IN/OUT 인자)가 없는 프로시저만** 지원합니다(`SimpleJdbcCall.execute()`를
인자 없이 호출하는 구조). 파라미터가 필요한 프로시저는 이 방식으로 호출할 수 없습니다.

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

### 스케줄 목록 조회 (페이징)
```bash
# 활성(사용중)만
GET /api/batch/schedules?pageIndex=1&pageSize=20

# 비활성 포함 전체
GET /api/batch/schedules/all?pageIndex=1&pageSize=20
```
`pageIndex`(기본 1)/`pageSize`(기본 20)는 생략 가능합니다. 응답은 `{ dataList: [...], totalCount: N }` 형태입니다.

```json
{
  "dataList": [ { "jobName": "documentSyncJob", "cronExpr": "0 30 15 * * *", "useYn": "Y", "...": "..." } ],
  "totalCount": 5
}
```

### 신규 스케줄 등록
```bash
POST /api/batch/schedules
Content-Type: application/json

{
  "jobName": "myNewJob",
  "cronExpr": "0 0 3 * * *",
  "taskletType": "SQL",
  "taskletConfig": "UPDATE DOC_TB SET STATUS='CLOSED' WHERE STATUS='PENDING'",
  "description": "장기 미처리 문서 정리",
  "useYn": "N"
}
```
이미 등록된 jobName이면 400으로 거절됩니다.

### 스케줄 전체 수정 (크론 + 타입 + 설정값)
```bash
PUT /api/batch/schedules/{jobName}
Content-Type: application/json

{ "cronExpr": "0 0 8 * * MON", "taskletType": "SQL", "taskletConfig": "...", "description": "..." }
```
크론만 바꾸고 싶으면 `cronExpr`만 채워서 보내도 됩니다. 저장 즉시 스케줄러에 반영됩니다(1분 폴링을 기다리지 않음).

### 스케줄 삭제
```bash
DELETE /api/batch/schedules/{jobName}
```
⚠️ `NATIVE` 타입(하드코딩된 Job)도 삭제할 수 있습니다. 다만 NATIVE는 실제 자바 `@Bean`(Job 클래스)이
이미 배포된 상태라, 이 API로 `BATCH_SCHEDULE` 행을 지워도 자바 코드/Bean 자체는 그대로 남고
**스케줄(크론 등록)만 사라집니다.** 다시 등록하려면 `POST /api/batch/schedules`로 동일한 `jobName` +
`taskletType: "NATIVE"`로 재등록하면 됩니다.

### 스케줄 활성/비활성 토글
```bash
# 단건 즉시 토글
PUT /api/batch/schedules/documentReportJob/toggle

# 다건 일괄 변경
PUT /api/batch/schedules/toggle-bulk
Content-Type: application/json

[ { "jobName": "documentSyncJob", "useYn": "N" }, { "jobName": "myNewJob", "useYn": "Y" } ]
```

---

## 실행 이력 조회 (페이징 + 검색조건)

```bash
# 배치별
GET /api/batch/jobs/documentReportJob/history?pageIndex=1&pageSize=10

# 배치별 + 기간검색 + 상태검색
GET /api/batch/jobs/documentReportJob/history?startDt=2026-07-01 00:00:00&endDt=2026-07-22 23:59:59&status=FAILED

# 전체 Job (jobName 필터 없음) + Job명 부분검색(LIKE) + 기간검색
GET /api/batch/history?jobName=document&startDt=2026-07-01 00:00:00&endDt=2026-07-22 23:59:59&status=COMPLETED
```

| 파라미터 | 설명 |
|---|---|
| `pageIndex` | 페이지 번호 (기본 1) |
| `pageSize` | 페이지당 건수 (기본 10) |
| `startDt` / `endDt` | 기간 검색 (`yyyy-MM-dd HH:mm:ss`) |
| `status` | 실행 상태 완전일치 검색 (`RUNNING` / `COMPLETED` / `FAILED` / `STOPPED` / `ABANDONED` / `UNKNOWN`) |
| `jobName` | (`/api/batch/history`에서만 사용) Job명 부분일치(LIKE, 대소문자 무시) 검색 |

응답은 스케줄 목록과 동일하게 `{ dataList: [...], totalCount: N }` 형태입니다.

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
