package cn.muxicloud.mop.model.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("mop_project")
public class ProjectEntity extends BaseEntity {
    private String code;
    private String name;
    private String domain;
    private String namespace;
    private String scopeInclude;
    private String scopeExclude;
    private String description;
    private Long ownerUserId;
    private Integer requireCqLink;
    private Long headRev;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getScopeInclude() { return scopeInclude; }
    public void setScopeInclude(String scopeInclude) { this.scopeInclude = scopeInclude; }
    public String getScopeExclude() { return scopeExclude; }
    public void setScopeExclude(String scopeExclude) { this.scopeExclude = scopeExclude; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public Integer getRequireCqLink() { return requireCqLink; }
    public void setRequireCqLink(Integer requireCqLink) { this.requireCqLink = requireCqLink; }
    public Long getHeadRev() { return headRev; }
    public void setHeadRev(Long headRev) { this.headRev = headRev; }
}
