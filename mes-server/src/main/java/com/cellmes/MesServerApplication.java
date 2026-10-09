package com.cellmes;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * cell-mes 教学参考实现 —— 电芯 MES 服务端。
 * 端口 8090，PostgreSQL 5433（cellmes 库），Flyway V1-V10 建表 + 种子数据。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.cellmes.master.mapper",
        "com.cellmes.trace.mapper",
        "com.cellmes.trace.dao",
        "com.cellmes.oee.mapper",
        "com.cellmes.spc.mapper",
        "com.cellmes.andon.mapper",
        "com.cellmes.formation.mapper"
})
public class MesServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MesServerApplication.class, args);
    }
}
