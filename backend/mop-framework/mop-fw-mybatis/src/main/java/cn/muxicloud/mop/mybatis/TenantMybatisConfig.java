package cn.muxicloud.mop.mybatis;

import cn.muxicloud.mop.tenant.TenantContext;
import cn.muxicloud.mop.tenant.TenantIgnore;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TenantMybatisConfig {
    private static final Set<String> IGNORED = Set.of("sys_tenant");

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.get();
                if (tenantId == null) {
                    throw new IllegalStateException("缺少租户上下文");
                }
                return new LongValue(tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TenantIgnore.ignored() || IGNORED.contains(tableName.toLowerCase());
            }
        }));
        return interceptor;
    }
}
