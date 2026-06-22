// =============================================
// BatchScheduleVo.java
// =============================================
package doc.batch.controller.service;

public class BatchScheduleVo {
    private String jobName;
    private String cronExpr;
    private String useYn;
    private String description;

    // getters / setters
    public String getJobName()              { return jobName; }
    public void setJobName(String jobName)  { this.jobName = jobName; }

    public String getCronExpr()                 { return cronExpr; }
    public void setCronExpr(String cronExpr)    { this.cronExpr = cronExpr; }

    public String getUseYn()                { return useYn; }
    public void setUseYn(String useYn)      { this.useYn = useYn; }

    public String getDescription()                  { return description; }
    public void setDescription(String description)  { this.description = description; }
}

