package cn.muxicloud.mop.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("mop_element")
public class ElementEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long branchId;
    private String uid;
    private String elementType;
    private String apiName;
    private String ownerUid;
    private Long revStart;
    private Long revEnd;
    private Integer tombstone;
    private String payload;
    private String contentHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public String getApiName() { return apiName; }
    public void setApiName(String apiName) { this.apiName = apiName; }
    public String getOwnerUid() { return ownerUid; }
    public void setOwnerUid(String ownerUid) { this.ownerUid = ownerUid; }
    public Long getRevStart() { return revStart; }
    public void setRevStart(Long revStart) { this.revStart = revStart; }
    public Long getRevEnd() { return revEnd; }
    public void setRevEnd(Long revEnd) { this.revEnd = revEnd; }
    public Integer getTombstone() { return tombstone; }
    public void setTombstone(Integer tombstone) { this.tombstone = tombstone; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
