package com.jinw.corpus.log;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CorpusStartupLog {

    @PostConstruct
    public void init() {

        log.info("Scan Corpus 启动成功");

    }

}