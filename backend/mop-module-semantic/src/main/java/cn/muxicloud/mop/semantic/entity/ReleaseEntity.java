package cn.muxicloud.mop.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("mop_release")
public class ReleaseEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String version;
    private Long branchId;
    private Long rev;
    private String compiledModel;
    private String owlObjectKey;
    private String shaclObjectKey;
    private Long qualityRunId;
    private Long cqRunId;
    private String status;
    private Long publishedBy;
    private LocalDateTime publishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getRev() { return rev; }
    public void setRev(Long rev) { this.rev = rev; }
    public String getCompiledModel() { return compiledModel; }
    public void setCompiledModel(String compiledModel) { this.compiledModel = compiledModel; }
    public String getOwlObjectKey() { return owlObjectKey; }
    public void setOwlObjectKey(String owlObjectKey) { this.owlObjectKey = owlObjectKey; }
    public String getShaclObjectKey() { return shaclObjectKey; }
    public void setShaclObjectKey(String shaclObjectKey) { this.shaclObjectKey = shaclObjectKey; }
    public Long getQualityRunId() { return qualityRunId; }
    public void setQualityRunId(Long qualityRunId) { this.qualityRunId = qualityRunId; }
    public Long getCqRunId() { return cqRunId; }
    public void setCqRunId(Long cqRunId) { this.cqRunId = cqRunId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
