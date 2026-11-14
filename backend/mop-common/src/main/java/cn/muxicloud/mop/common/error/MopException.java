package cn.muxicloud.mop.common.error;

public class MopException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object details;

    public MopException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), null);
    }

    public MopException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object details() {
        return details;
    }
}
