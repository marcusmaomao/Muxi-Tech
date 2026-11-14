package cn.muxicloud.mop.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAuditRecorder implements AuditRecorder {
    private static final Logger log = LoggerFactory.getLogger(LoggingAuditRecorder.class);

    @Override
    public void record(String category, String action, String target, String result, String detail) {
        log.info("audit category={} action={} target={} result={} detail={}", category, action, target, result, detail);
    }
}
