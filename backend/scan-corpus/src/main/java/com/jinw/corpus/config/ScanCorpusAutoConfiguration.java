package com.jinw.corpus.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.jinw.corpus")
@ConditionalOnProperty(
        prefix = "cscan.corpus",
        name = "enabled",
        havingValue = "true"
)
public class ScanCorpusAutoConfiguration {

}