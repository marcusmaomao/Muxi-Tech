package cn.muxicloud.mop.system.entity;

import cn.muxicloud.mop.mybatis.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_dict_item")
public class DictItemEntity extends BaseEntity {
    private Long typeId;
    private String label;
    private String itemValue;
    private Integer sortNo;

    public Long getTypeId() { return typeId; }
    public void setTypeId(Long typeId) { this.typeId = typeId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getItemValue() { return itemValue; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }
    public Integer getSortNo() { return sortNo; }
    public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
}
