package com.jinw.web.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jinw.web.domain.BTLlmConfig;
import com.jinw.web.domain.BTLlmResult;
import com.jinw.web.domain.LlmQuestionResult;

public interface BTLlmConfigService extends IService<BTLlmConfig> {

    String create(BTLlmConfig btLlmConfig);

    String deleteById(String id);

    String update(BTLlmConfig btLlmConfig);

    BTLlmConfig getById(String id);

    String adaptFile(String fileId);

    LlmQuestionResult fileAdaptResult(String fileId);

    String changeLlmConfig(String id);

    String adaptProject(String taskId);
}