package cn.muxicloud.mop.basic.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({DatasourceSecretProperties.class, StorageProperties.class})
public class BasicConfig {
}
