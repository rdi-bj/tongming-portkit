package com.jinw;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.jinw.web.mapper")
@MapperScan("com.jinw.corpus.mapper")
@EnableScheduling
public class ScanApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScanApplication.class, args);
    }

}