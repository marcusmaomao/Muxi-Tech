package cn.muxicloud.mop.system.security;

import cn.muxicloud.mop.system.service.AccessService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {
    private final AccessService accessService;

    public PermissionAspect(AccessService accessService) {
        this.accessService = accessService;
    }

    @Before("@annotation(permission)")
    public void check(RequiresPermission permission) {
        accessService.require(permission.value());
    }
}
