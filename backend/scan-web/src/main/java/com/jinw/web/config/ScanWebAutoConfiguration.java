package com.jinw.web.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.jinw.web")
@ConditionalOnProperty(
        prefix = "cscan.web",
        name = "enabled",
        havingValue = "true"
)
public class ScanWebAutoConfiguration {

}