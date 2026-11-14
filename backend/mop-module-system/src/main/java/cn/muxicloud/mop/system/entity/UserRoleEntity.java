package cn.muxicloud.mop.system.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user_role")
public class UserRoleEntity extends BaseEntity {
    private Long userId;
    private Long roleId;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }
}
