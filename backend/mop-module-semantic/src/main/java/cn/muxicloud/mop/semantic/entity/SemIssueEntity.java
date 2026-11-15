package cn.muxicloud.mop.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sem_issue")
public class SemIssueEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long runId;
    private String checkCode;
    private String severity;
    private String elementUid;
    private String focusNode;
    @TableField("path")
    private String resultPath;
    private String message;
    private String state;
    private String ignoreReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }
    public String getCheckCode() { return checkCode; }
    public void setCheckCode(String checkCode) { this.checkCode = checkCode; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getElementUid() { return elementUid; }
    public void setElementUid(String elementUid) { this.elementUid = elementUid; }
    public String getFocusNode() { return focusNode; }
    public void setFocusNode(String focusNode) { this.focusNode = focusNode; }
    public String getResultPath() { return resultPath; }
    public void setResultPath(String resultPath) { this.resultPath = resultPath; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getIgnoreReason() { return ignoreReason; }
    public void setIgnoreReason(String ignoreReason) { this.ignoreReason = ignoreReason; }
}
