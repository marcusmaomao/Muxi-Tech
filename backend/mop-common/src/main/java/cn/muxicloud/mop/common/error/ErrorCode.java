package cn.muxicloud.mop.common.error;

/**
 * 平台错误码。完整清单见技术文档附录，这里只放脚手架已经会返回的码。
 */
public enum ErrorCode {
    UNAUTHENTICATED("MOP-1001", "未认证", 401),
    BAD_CREDENTIALS("MOP-1002", "用户名或密码错误", 401),
    FORBIDDEN("MOP-1003", "无权限", 403),
    VALIDATION("MOP-4000", "请求参数不合法", 400),
    INTERNAL("MOP-5000", "系统内部错误", 500);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String code, String defaultMessage, int httpStatus) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
