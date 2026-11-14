package cn.muxicloud.mop.web.api;

import cn.muxicloud.mop.web.trace.TraceIds;

public record ApiResponse<T>(String code, String message, T data, Object details, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "成功", data, null, TraceIds.current());
    }

    public static ApiResponse<Void> error(String code, String message, Object details) {
        return new ApiResponse<>(code, message, null, details, TraceIds.current());
    }
}
