package cn.muxicloud.mop.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mop_commit")
public class CommitEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long branchId;
    private Long rev;
    private Long parentRev;
    private Long mergeSourceBranchId;
    private Long mergeSourceRev;
    private String message;
    private String source;
    private Integer changeCount;
    private Long authorId;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getRev() { return rev; }
    public void setRev(Long rev) { this.rev = rev; }
    public Long getParentRev() { return parentRev; }
    public void setParentRev(Long parentRev) { this.parentRev = parentRev; }
    public Long getMergeSourceBranchId() { return mergeSourceBranchId; }
    public void setMergeSourceBranchId(Long mergeSourceBranchId) { this.mergeSourceBranchId = mergeSourceBranchId; }
    public Long getMergeSourceRev() { return mergeSourceRev; }
    public void setMergeSourceRev(Long mergeSourceRev) { this.mergeSourceRev = mergeSourceRev; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Integer getChangeCount() { return changeCount; }
    public void setChangeCount(Integer changeCount) { this.changeCount = changeCount; }
    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
