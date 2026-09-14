package com.lumiinsight;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@MapperScan("com.lumiinsight.modules.**.mapper")
public class LumiInsightApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiInsightApplication.class, args);
    }
}
