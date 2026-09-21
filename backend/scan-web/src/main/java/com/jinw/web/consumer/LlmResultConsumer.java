package com.jinw.web.consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.common.constant.AdaptStatus;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.LlmQuestionInfo;
import com.jinw.common.domain.LlmResultMessage;
import com.jinw.common.domain.RiscvAnalysisResult;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.domain.BTLlmResult;
import com.jinw.web.domain.BTQuestionFileInfo;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.mapper.BTAppInfoMapper;
import com.jinw.web.mapper.BTLlmResultMapper;
import com.jinw.web.mapper.BTQuestionFileInfoMapper;
import com.jinw.web.mapper.BTQuestionInfoMapper;
import com.jinw.web.util.FileStorageUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.File;

import static com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine.UNZIP_DIR;

@Slf4j
@Component
public class LlmResultConsumer {

    private final BTLlmResultMapper bTLlmResultMapper;
    private final BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    private final BTQuestionInfoMapper bTQuestionInfoMapper;
    private final BTAppInfoMapper bTAppInfoMapper;

    public LlmResultConsumer(BTLlmResultMapper bTLlmResultMapper, BTQuestionFileInfoMapper bTQuestionFileInfoMapper, BTQuestionInfoMapper bTQuestionInfoMapper, BTAppInfoMapper bTAppInfoMapper) {
        this.bTLlmResultMapper = bTLlmResultMapper;
        this.bTQuestionFileInfoMapper = bTQuestionFileInfoMapper;
        this.bTQuestionInfoMapper = bTQuestionInfoMapper;
        this.bTAppInfoMapper = bTAppInfoMapper;
    }

    @RabbitListener(
            queues = ScanConstant.LLM_FILE_RESULT_QUEUE,
            containerFactory = "llmListenerContainerFactory"
    )
    public void consume(
            LlmResultMessage result,
            Message message,
            Channel channel
    ) throws Exception {

        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();

        try {
//            for (MigrationChange change : result.getLlmResponse().getChanges()) {
//                BTLlmResult btLlmResult = new BTLlmResult();
//                btLlmResult.setTaskId(result.getTaskId());
//                btLlmResult.setFilePath(FileStorageUtil.toRelativePath(result.getTaskId() + File.separator + UNZIP_DIR,result.getQuestionFilePath()));
//                btLlmResult.setType(change.getType());
//                btLlmResult.setOldLine(change.getOldLine());
//                btLlmResult.setNewLine(change.getNewLine());
//                btLlmResult.setOldCode(change.getOldCode());
//                btLlmResult.setNewCode(change.getNewCode());
//                btLlmResult.setDescription(change.getDescription());
//                bTLlmResultMapper.insert(btLlmResult);
//            }
//            BTLlmResult btLlmResult = new BTLlmResult();
//            btLlmResult.setTaskId(result.getTaskId());
//            btLlmResult.setFilePath(FileStorageUtil.toRelativePath(result.getTaskId() + File.separator + UNZIP_DIR, result.getQuestionFilePath()));
//            btLlmResult.setNewCode(result.getNewCode());
//            bTLlmResultMapper.insert(btLlmResult);
            String fileId = result.getFileId();
            BTQuestionFileInfo btQuestionFileInfo = bTQuestionFileInfoMapper.selectById(fileId);
            BTAppInfo btAppInfo = bTAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", btQuestionFileInfo.getTaskId()));
            if (!result.getStatus().equals(btQuestionFileInfo.getStatus())){
                btQuestionFileInfo.setStatus(result.getStatus());
            }
            if (result.getLlmQuestionInfo() != null){
                LlmQuestionInfo llmQuestionInfo = result.getLlmQuestionInfo();
                String questionInfoId = llmQuestionInfo.getId();
                BTQuestionInfo btQuestionInfo = bTQuestionInfoMapper.selectById(questionInfoId);
                btQuestionInfo.setStatus(AdaptStatus.FINISH.name());
                btQuestionInfo.setAdaptResult(llmQuestionInfo.getDescription());
                bTQuestionInfoMapper.updateById(btQuestionInfo);
                btQuestionFileInfo.setAdaptQuestionCount(btQuestionFileInfo.getAdaptQuestionCount() + 1);
                btAppInfo.setAdaptQuestion(btAppInfo.getAdaptQuestion() + 1);
            }
            RiscvAnalysisResult riscvAnalysisResult = result.getRiscvAnalysisResult();
            if (riscvAnalysisResult != null){
                riscvAnalysisResult.setOriginalFilePath(FileStorageUtil.toRelativePath(result.getTaskId(),riscvAnalysisResult.getOriginalFilePath()));
                riscvAnalysisResult.setSuggestedRiscvFilePath(FileStorageUtil.toRelativePath(result.getTaskId(),riscvAnalysisResult.getSuggestedRiscvFilePath()));
                btQuestionFileInfo.setAdaptResult(JSONUtil.toJsonStr(riscvAnalysisResult));
            }
            bTQuestionFileInfoMapper.updateById(btQuestionFileInfo);
            if (!result.getSessionId().equals(btAppInfo.getSessionId())){
                btAppInfo.setSessionId(result.getSessionId());
                log.info("{}的sessionId已更新为{}",btAppInfo.getId(),result.getSessionId());
            }
            if (AdaptStatus.FINISH.name().equals(result.getStatus())){
                btAppInfo.setAdaptQuestionFile(btAppInfo.getAdaptQuestionFile() + 1);
            }
            if (btAppInfo.getAdaptQuestion() == btAppInfo.getTotalQuestion() && btAppInfo.getAdaptQuestionFile() == btAppInfo.getTotalQuestionFile()){
                log.info("{}已经适配完成",btAppInfo.getId());
                btAppInfo.setAdaptStatus("-1");
            }
            bTAppInfoMapper.updateById(btAppInfo);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {

            log.error("消费失败", e);
            channel.basicNack(
                    deliveryTag,
                    false,
                    true
            );
        }
    }
}