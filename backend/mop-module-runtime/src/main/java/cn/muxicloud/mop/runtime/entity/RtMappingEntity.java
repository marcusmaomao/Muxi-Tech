package cn.muxicloud.mop.runtime.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("rt_mapping")
public class RtMappingEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long branchId;
    private Long datasourceId;
    private String objectUid;
    private Integer versionNo;
    private String definition;
    private Long createdBy;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public Long getDatasourceId() { return datasourceId; }
    public void setDatasourceId(Long datasourceId) { this.datasourceId = datasourceId; }
    public String getObjectUid() { return objectUid; }
    public void setObjectUid(String objectUid) { this.objectUid = objectUid; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getDefinition() { return definition; }
    public void setDefinition(String definition) { this.definition = definition; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
