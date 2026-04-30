package com.vemo.codereview;

import java.util.TimeZone;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan("com.vemo.codereview.**.mapper")
@ConfigurationPropertiesScan("com.vemo.codereview.common.config")
@SpringBootApplication
public class CodeReviewerApplication {

    static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    public static void main(String[] args) {
        configureDefaultTimeZone();
        SpringApplication.run(CodeReviewerApplication.class, args);
    }

    static void configureDefaultTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    }
}
