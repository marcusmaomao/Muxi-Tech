package cn.muxicloud.mop.server;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.muxicloud.mop")
@MapperScan(basePackages = "cn.muxicloud.mop", annotationClass = Mapper.class)
public class MopServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MopServerApplication.class, args);
    }
}
