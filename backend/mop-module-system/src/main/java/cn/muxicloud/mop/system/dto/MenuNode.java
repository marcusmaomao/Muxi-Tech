package cn.muxicloud.mop.system.dto;

public record MenuNode(Long id, Long parentId, String name, String path, String menuType, String permission) {
}
