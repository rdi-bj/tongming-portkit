package com.jinw.corpus.task;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.common.domain.LlmConfig;
import com.jinw.common.domain.RiscvAnalysisResult;
import com.jinw.corpus.domain.BTCorpusInfo;
import com.jinw.corpus.domain.BTCorpusJsonl;
import com.jinw.corpus.mapper.BTCorpusInfoMapper;
import com.jinw.corpus.mapper.BTCorpusJsonlMapper;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.domain.BTLlmConfig;
import com.jinw.web.domain.BTQuestionFileInfo;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.mapper.BTAppInfoMapper;
import com.jinw.web.mapper.BTLlmConfigMapper;
import com.jinw.web.mapper.BTQuestionFileInfoMapper;
import com.jinw.web.mapper.BTQuestionInfoMapper;
import com.jinw.web.service.impl.statemachine.ScanState;
import com.jinw.corpus.utils.OpenCodeLLMClient;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.LlmConfigValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

import static com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine.UNZIP_DIR;

@Component
@Slf4j
@ConditionalOnExpression("'${cscan.corpus.enabled:false}'.equals('true')")
public class CorpusScheduledTask {

    private final BTAppInfoMapper bTAppInfoMapper;
    private final BTCorpusInfoMapper bTCorpusInfoMapper;
    private final BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    private final BTQuestionInfoMapper bTQuestionInfoMapper;
    private final OpenCodeLLMClient openCodeLLMClient;
    private final BTLlmConfigMapper bTLlmConfigMapper;
    private final BTCorpusJsonlMapper bTCorpusJsonlMapper;

    public CorpusScheduledTask(BTAppInfoMapper bTAppInfoMapper, BTCorpusInfoMapper bTCorpusInfoMapper, BTQuestionFileInfoMapper bTQuestionFileInfoMapper, BTQuestionInfoMapper bTQuestionInfoMapper, OpenCodeLLMClient openCodeLLMClient, BTLlmConfigMapper bTLlmConfigMapper, BTCorpusJsonlMapper bTCorpusJsonlMapper) {
        this.bTAppInfoMapper = bTAppInfoMapper;
        this.bTCorpusInfoMapper = bTCorpusInfoMapper;
        this.bTQuestionFileInfoMapper = bTQuestionFileInfoMapper;
        this.bTQuestionInfoMapper = bTQuestionInfoMapper;
        this.openCodeLLMClient = openCodeLLMClient;
        this.bTLlmConfigMapper = bTLlmConfigMapper;
        this.bTCorpusJsonlMapper = bTCorpusJsonlMapper;
    }

    @Scheduled(cron = "0 */10 * * * ?")
    public void startCollectCorpus() {
        log.info("定时任务已经启动");
        try{
            List<BTAppInfo> successScanApp = bTAppInfoMapper.selectList(new QueryWrapper<BTAppInfo>()
                    .eq("STATUS", ScanState.SUCCESS.name()));
            for (BTAppInfo btAppInfo : successScanApp) {
                String fileMd5 = btAppInfo.getFileMd5();
                BTCorpusInfo corpusInfo = bTCorpusInfoMapper.selectOne(new QueryWrapper<BTCorpusInfo>().eq("FILE_MD5", fileMd5));
                if (corpusInfo == null){
                    log.info("开始收集语料");
                    BTLlmConfig btLlmConfig = bTLlmConfigMapper.selectOne(new QueryWrapper<BTLlmConfig>().eq("ENABLED", "1"));
                    LlmConfigValidator.validate(btLlmConfig);
                    LlmConfig llmConfig = new LlmConfig();
                    BeanUtil.copyProperties(btLlmConfig, llmConfig);
                    Boolean haveLlmConfig = openCodeLLMClient.judgeHaveLlmConfig(llmConfig);
                    if (!haveLlmConfig){
                        log.info("已更新大模型配置");
                        openCodeLLMClient.updateLlmConfig(llmConfig);
                    }else {
                        log.info("无需更新大模型配置");
                    }
                    corpusInfo = new BTCorpusInfo();
                    corpusInfo.setName(btAppInfo.getName());
                    corpusInfo.setFileMd5(btAppInfo.getFileMd5());
                    corpusInfo.setFileName(btAppInfo.getFileName());
                    corpusInfo.setTaskId(btAppInfo.getTaskId());
                    bTCorpusInfoMapper.insert(corpusInfo);

                    List<BTQuestionFileInfo> btQuestionFileInfos = bTQuestionFileInfoMapper.selectList(new QueryWrapper<BTQuestionFileInfo>()
                            .eq("TASK_ID", btAppInfo.getTaskId())
                            .eq("AI_VERIFY_STATUS", "1")
                            .eq("HIT_KNOWLEDGE", "1"));

                    for (BTQuestionFileInfo btQuestionFileInfo : btQuestionFileInfos) {
                        List<BTQuestionInfo> btQuestionInfos = bTQuestionInfoMapper.selectList(new QueryWrapper<BTQuestionInfo>()
                                .eq("TASK_ID", btAppInfo.getTaskId())
                                .eq("AI_VERIFY_STATUS", "1")
                                .eq("FILE_ID", btQuestionFileInfo.getId())
                                .eq("HIT_KNOWLEDGE", "1"));
                        for (BTQuestionInfo btQuestionInfo : btQuestionInfos) {
                            String sessionId = openCodeLLMClient.getOrCreateSession(
                                    null,
                                    openCodeLLMClient.convertTaskIdToTitle("corpus",btQuestionInfo.getTaskId()),
                                    FileStorageUtil.toAbsolutePath("", btQuestionInfo.getTaskId()),
                                    llmConfig);
                            String prompt = openCodeLLMClient.buildCorpusPrompt(
                                    FileStorageUtil.toAbsolutePath(
                                            btQuestionInfo.getTaskId() + File.separator + UNZIP_DIR, btQuestionInfo.getFilePath()), btQuestionInfo.getStartLine(), btQuestionInfo.getEndLine());
                            BTCorpusJsonl btCorpusJsonl = openCodeLLMClient.sendFilePrompt(
                                    sessionId,
                                    prompt,
                                    BTCorpusJsonl.class);
                            btCorpusJsonl.setCorpusId(corpusInfo.getId());
                            bTCorpusJsonlMapper.insert(btCorpusJsonl);
                        }
                    }
                    break;
                }
            }
        } catch (BusinessException e) {
            log.error("语料收集业务异常: {}", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
