package cn.muxicloud.mop.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请填写用户名") @Size(max = 64, message = "用户名过长") String username,
        @NotBlank(message = "请填写密码") @Size(min = 6, max = 72, message = "密码长度需要在 6 到 72 之间") String password) {
}
