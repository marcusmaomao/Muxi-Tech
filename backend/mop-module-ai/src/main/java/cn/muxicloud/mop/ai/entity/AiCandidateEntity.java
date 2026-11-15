package cn.muxicloud.mop.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("ai_candidate")
public class AiCandidateEntity {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private Long jobId;
    private String candidateKey;
    private String elementType;
    private String payload;
    private String evidenceQuote;
    private Integer blockNo;
    private Double confidence;
    private String state;
    private String adoptedUid;
    private String message;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getCandidateKey() { return candidateKey; }
    public void setCandidateKey(String candidateKey) { this.candidateKey = candidateKey; }
    public String getElementType() { return elementType; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getEvidenceQuote() { return evidenceQuote; }
    public void setEvidenceQuote(String evidenceQuote) { this.evidenceQuote = evidenceQuote; }
    public Integer getBlockNo() { return blockNo; }
    public void setBlockNo(Integer blockNo) { this.blockNo = blockNo; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getAdoptedUid() { return adoptedUid; }
    public void setAdoptedUid(String adoptedUid) { this.adoptedUid = adoptedUid; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
