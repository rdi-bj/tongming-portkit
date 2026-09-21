package com.jinw.worker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.jinw.worker")
@ConditionalOnProperty(
        prefix = "cscan.worker",
        name = "enabled",
        havingValue = "true"
)
public class ScanWorkerAutoConfiguration {

}