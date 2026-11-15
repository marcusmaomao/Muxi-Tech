package cn.muxicloud.mop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_evidence")
public class AiEvidenceEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long jobId;
    private Long fileId;
    private Integer blockNo;
    private String content;
    private String contentHash;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Integer getBlockNo() { return blockNo; }
    public void setBlockNo(Integer blockNo) { this.blockNo = blockNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
}
