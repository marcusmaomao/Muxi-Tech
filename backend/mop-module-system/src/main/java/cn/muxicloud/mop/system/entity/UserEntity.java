package cn.muxicloud.mop.system.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user")
public class UserEntity extends BaseEntity {
    private String uid;
    private String username;
    private String passwordHash;
    private String displayName;
    private Integer status;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
