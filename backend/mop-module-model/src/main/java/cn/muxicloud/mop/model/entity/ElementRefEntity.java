package cn.muxicloud.mop.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("mop_element_ref")
public class ElementRefEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long branchId;
    private String fromUid;
    private String toUid;
    private String refType;
    private Long revStart;
    private Long revEnd;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }
    public String getToUid() { return toUid; }
    public void setToUid(String toUid) { this.toUid = toUid; }
    public String getRefType() { return refType; }
    public void setRefType(String refType) { this.refType = refType; }
    public Long getRevStart() { return revStart; }
    public void setRevStart(Long revStart) { this.revStart = revStart; }
    public Long getRevEnd() { return revEnd; }
    public void setRevEnd(Long revEnd) { this.revEnd = revEnd; }
}
