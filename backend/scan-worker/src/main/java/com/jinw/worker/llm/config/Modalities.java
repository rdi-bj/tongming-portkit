package com.jinw.worker.llm.config;

import lombok.Data;

import java.util.List;

@Data
public class Modalities {
    private List<String> input;
    private List<String> output;
}