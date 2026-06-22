// =============================================
// BatchScheduleVo.java
// =============================================
package doc.batch.controller.service;

import java.util.Date;

public class BatchRunHistVo {
    private Long   runId;
    private String jobName;
    private String stepName;        // null = 전체 실행
    private String triggeredBy;
    private String triggerType;     // AUTO | MANUAL
    private Date   startTime;
    private Date   endTime;
    private String status;
    private String errorMsg;
    private Long   executionId;

    // getters / setters
    public Long   getRunId()                    { return runId; }
    public void   setRunId(Long runId)          { this.runId = runId; }

    public String getJobName()                  { return jobName; }
    public void   setJobName(String jobName)    { this.jobName = jobName; }

    public String getStepName()                 { return stepName; }
    public void   setStepName(String stepName)  { this.stepName = stepName; }

    public String getTriggeredBy()                      { return triggeredBy; }
    public void   setTriggeredBy(String triggeredBy)    { this.triggeredBy = triggeredBy; }

    public String getTriggerType()                      { return triggerType; }
    public void   setTriggerType(String triggerType)    { this.triggerType = triggerType; }

    public Date   getStartTime()                { return startTime; }
    public void   setStartTime(Date startTime)  { this.startTime = startTime; }

    public Date   getEndTime()                  { return endTime; }
    public void   setEndTime(Date endTime)      { this.endTime = endTime; }

    public String getStatus()                   { return status; }
    public void   setStatus(String status)      { this.status = status; }

    public String getErrorMsg()                 { return errorMsg; }
    public void   setErrorMsg(String errorMsg)  { this.errorMsg = errorMsg; }

    public Long   getExecutionId()                      { return executionId; }
    public void   setExecutionId(Long executionId)      { this.executionId = executionId; }
}
