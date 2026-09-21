package com.jinw.worker.log;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WorkerStartupLog {

    @PostConstruct
    public void init() {

        log.info("Scan Worker 启动成功");

    }

}