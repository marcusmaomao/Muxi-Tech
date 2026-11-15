package cn.muxicloud.mop.ai.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

@TableName("ai_model")
public class AiModelEntity extends BaseEntity {
    private String displayName;
    private String modelName;
    private String vendor;
    private String baseUrl;
    @JsonIgnore
    private String apiKeyCipher;
    private Integer rpmLimit;
    private Integer enabled;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKeyCipher() { return apiKeyCipher; }
    public void setApiKeyCipher(String apiKeyCipher) { this.apiKeyCipher = apiKeyCipher; }
    public Integer getRpmLimit() { return rpmLimit; }
    public void setRpmLimit(Integer rpmLimit) { this.rpmLimit = rpmLimit; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}
