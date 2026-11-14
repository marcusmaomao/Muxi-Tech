package cn.muxicloud.mop.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.web.trace.TraceIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clear() {
        MDC.clear();
    }

    @Test
    void mopExceptionKeepsCodeAndTrace() {
        MDC.put(TraceIds.MDC_KEY, "trace-1");
        var response = handler.handleMop(new MopException(ErrorCode.BAD_CREDENTIALS));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MOP-1002");
        assertThat(response.getBody().message()).isEqualTo("用户名或密码错误");
        assertThat(response.getBody().traceId()).isEqualTo("trace-1");
        assertThat(response.getBody().data()).isNull();
    }
}
