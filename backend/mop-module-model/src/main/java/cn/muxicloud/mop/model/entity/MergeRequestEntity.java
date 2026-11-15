package cn.muxicloud.mop.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mop_merge_request")
public class MergeRequestEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long sourceBranchId;
    private Long targetBranchId;
    private Long baseRev;
    private Long sourceRev;
    private Long targetRev;
    private String title;
    private String description;
    private String status;
    private Long qualityRunId;
    private Long cqRunId;
    private String conflicts;
    private String resolutions;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long mergedBy;
    private LocalDateTime mergedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getSourceBranchId() { return sourceBranchId; }
    public void setSourceBranchId(Long sourceBranchId) { this.sourceBranchId = sourceBranchId; }
    public Long getTargetBranchId() { return targetBranchId; }
    public void setTargetBranchId(Long targetBranchId) { this.targetBranchId = targetBranchId; }
    public Long getBaseRev() { return baseRev; }
    public void setBaseRev(Long baseRev) { this.baseRev = baseRev; }
    public Long getSourceRev() { return sourceRev; }
    public void setSourceRev(Long sourceRev) { this.sourceRev = sourceRev; }
    public Long getTargetRev() { return targetRev; }
    public void setTargetRev(Long targetRev) { this.targetRev = targetRev; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getQualityRunId() { return qualityRunId; }
    public void setQualityRunId(Long qualityRunId) { this.qualityRunId = qualityRunId; }
    public Long getCqRunId() { return cqRunId; }
    public void setCqRunId(Long cqRunId) { this.cqRunId = cqRunId; }
    public String getConflicts() { return conflicts; }
    public void setConflicts(String conflicts) { this.conflicts = conflicts; }
    public String getResolutions() { return resolutions; }
    public void setResolutions(String resolutions) { this.resolutions = resolutions; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getMergedBy() { return mergedBy; }
    public void setMergedBy(Long mergedBy) { this.mergedBy = mergedBy; }
    public LocalDateTime getMergedAt() { return mergedAt; }
    public void setMergedAt(LocalDateTime mergedAt) { this.mergedAt = mergedAt; }
}
