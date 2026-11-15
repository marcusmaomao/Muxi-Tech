package cn.muxicloud.mop.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mop_branch")
public class BranchEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String name;
    private Long parentBranchId;
    private Long forkRev;
    private Long headRev;
    @TableField("protected")
    private Integer protectedFlag;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getParentBranchId() { return parentBranchId; }
    public void setParentBranchId(Long parentBranchId) { this.parentBranchId = parentBranchId; }
    public Long getForkRev() { return forkRev; }
    public void setForkRev(Long forkRev) { this.forkRev = forkRev; }
    public Long getHeadRev() { return headRev; }
    public void setHeadRev(Long headRev) { this.headRev = headRev; }
    public Integer getProtectedFlag() { return protectedFlag; }
    public void setProtectedFlag(Integer protectedFlag) { this.protectedFlag = protectedFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
