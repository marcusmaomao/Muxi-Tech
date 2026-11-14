package cn.muxicloud.mop.system.dto;

import java.util.List;

public record SessionProfile(UserProfile user, List<String> permissions, List<MenuNode> menus) {
}
