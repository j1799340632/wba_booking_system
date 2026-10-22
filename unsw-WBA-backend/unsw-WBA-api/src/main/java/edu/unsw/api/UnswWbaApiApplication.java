package edu.unsw.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 17/6/2026 17:23
 * @description Main class
 */
@SpringBootApplication(scanBasePackages = {"edu.unsw"})
@MapperScan(basePackages = {"edu.unsw.dao.mapper", "edu.unsw.core.mapper"})
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class UnswWbaApiApplication {
    static void main(String[] args) {
        SpringApplication.run(UnswWbaApiApplication.class, args);
    }
}
