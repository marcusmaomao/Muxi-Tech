package cn.muxicloud.mop.system.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_notice")
public class NoticeEntity extends BaseEntity {
    private String title;
    private String content;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
