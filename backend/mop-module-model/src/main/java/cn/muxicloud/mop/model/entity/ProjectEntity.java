package cn.muxicloud.mop.model.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("mop_project")
public class ProjectEntity extends BaseEntity {
    private String code;
    private String name;
    private String namespace;
    private String description;
    private Long headRev;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getHeadRev() { return headRev; }
    public void setHeadRev(Long headRev) { this.headRev = headRev; }
}
