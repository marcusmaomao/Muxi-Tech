package cn.muxicloud.mop.web.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 写接口的幂等键先透传并记入日志。真正的结果缓存留到动作引擎（M3）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdempotencyKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "Idempotency-Key";
    static final String MDC_KEY = "idempotencyKey";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key != null && !key.isBlank()) {
            String trimmed = key.trim();
            MDC.put(MDC_KEY, trimmed);
            response.setHeader(HEADER, trimmed);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
