package cn.muxicloud.mop.system.security;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.system.entity.OperLogEntity;
import cn.muxicloud.mop.system.mapper.OperLogMapper;
import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.web.trace.TraceIds;
import java.time.LocalDateTime;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class OperLogAspect {
    private final OperLogMapper operLogMapper;

    public OperLogAspect(OperLogMapper operLogMapper) {
        this.operLogMapper = operLogMapper;
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        try {
            Object value = joinPoint.proceed();
            write(operLog, true);
            return value;
        } catch (RuntimeException ex) {
            write(operLog, false);
            throw ex;
        }
    }

    private void write(OperLog operLog, boolean success) {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            return;
        }
        OperLogEntity row = new OperLogEntity();
        row.setTenantId(tenantId);
        row.setActorId(actorId());
        row.setTitle(operLog.title());
        row.setAction(operLog.action());
        row.setSuccess(success ? 1 : 0);
        row.setTraceId(TraceIds.current());
        row.setCreatedAt(LocalDateTime.now());
        operLogMapper.insert(row);
    }

    private static Long actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        return null;
    }
}
