// =============================================
// BatchScheduleVo.java
// =============================================
package doc.batch.controller.service;

import java.util.Date;

public class BatchScheduleVo {
    private String jobName;
    private String cronExpr;
    private String useYn;
    private String description;

    // 동적 Job 등록 확장 필드
    private String taskletType;     // NATIVE | SQL | PROCEDURE | API
    private String taskletConfig;   // SQL문 / 프로시저 호출 스펙 / API URL
    private Date   regDt;
    private Date   updDt;

    private String modifiedBy;

    // getters / setters
    public String getJobName()              { return jobName; }
    public void setJobName(String jobName)  { this.jobName = jobName; }

    public String getCronExpr()                 { return cronExpr; }
    public void setCronExpr(String cronExpr)    { this.cronExpr = cronExpr; }

    public String getUseYn()                { return useYn; }
    public void setUseYn(String useYn)      { this.useYn = useYn; }

    public String getDescription()                  { return description; }
    public void setDescription(String description)  { this.description = description; }

    public String getTaskletType()                    { return taskletType; }
    public void setTaskletType(String taskletType)    { this.taskletType = taskletType; }

    public String getTaskletConfig()                      { return taskletConfig; }
    public void setTaskletConfig(String taskletConfig)    { this.taskletConfig = taskletConfig; }

    public Date getRegDt()             { return regDt; }
    public void setRegDt(Date regDt)  { this.regDt = regDt; }

    public Date getUpdDt()             { return updDt; }
    public void setUpdDt(Date updDt)  { this.updDt = updDt; }

    public String getModifiedBy()                    { return modifiedBy; }
    public void setModifiedBy(String modifiedBy)     { this.modifiedBy = modifiedBy; }

    /** NATIVE 타입(하드코딩 Java Job) 여부 - null/빈값도 NATIVE로 취급(레거시 3개 Job 호환) */
    public boolean isNative() {
        return taskletType == null || taskletType.trim().isEmpty() || "NATIVE".equalsIgnoreCase(taskletType);
    }
}
