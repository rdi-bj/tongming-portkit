package com.jinw.worker.consumer;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.*;
import com.jinw.mq.task.VerifyQueuePublisher;
import com.jinw.worker.llm.utils.OpenCodeLLMClient;
import com.jinw.worker.task.TaskActivityTracker;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VerifyTaskConsumer {


    private final OpenCodeLLMClient openCodeLLMClient;
    private final VerifyQueuePublisher verifyQueuePublisher;
    private final TaskActivityTracker taskActivityTracker;

    public VerifyTaskConsumer(OpenCodeLLMClient openCodeLLMClient, VerifyQueuePublisher verifyQueuePublisher, TaskActivityTracker taskActivityTracker) {
        this.openCodeLLMClient = openCodeLLMClient;
        this.verifyQueuePublisher = verifyQueuePublisher;
        this.taskActivityTracker = taskActivityTracker;
    }

    @RabbitListener(
            queues = ScanConstant.VERIFY_FILE_TASK_QUEUE,
            containerFactory = "llmListenerContainerFactory"
    )
    public void consume(
            VerifyTaskMessage verifyTaskMessage,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String taskId = verifyTaskMessage.getTaskId();
        taskActivityTracker.begin(taskId, null);
        try {
            VerifyStartMessage verifyStartMessage = new VerifyStartMessage();
            verifyStartMessage.setTaskId(verifyTaskMessage.getTaskId());
            verifyStartMessage.setFilePath(verifyTaskMessage.getFilePath());
            verifyStartMessage.setTotalNum(verifyTaskMessage.getQuestionInfos().size());
            verifyQueuePublisher.sendFileStart(verifyStartMessage);

            // 先更新配置
            Boolean haveLlmConfig = openCodeLLMClient.judgeHaveLlmConfig(verifyTaskMessage.getLlmConfig());
            if (!haveLlmConfig){
                log.info("已更新大模型配置");
                openCodeLLMClient.updateLlmConfig(verifyTaskMessage.getLlmConfig());
            }else {
                log.info("无需更新大模型配置");
            }
            VerifyResultMessage verifyResultMessage = new VerifyResultMessage();
            verifyResultMessage.setTaskId(verifyTaskMessage.getTaskId());
            verifyResultMessage.setFileId(verifyTaskMessage.getFileId());
            verifyResultMessage.setFilePath(verifyTaskMessage.getFilePath());
            verifyResultMessage.setTotalNum(verifyTaskMessage.getQuestionInfos().size());
            verifyResultMessage.setWaitNum(verifyTaskMessage.getQuestionInfos().size());
            String sessionId = openCodeLLMClient.getOrCreateSession(
                    null,
                    openCodeLLMClient.convertTaskIdToTitle("verify",verifyTaskMessage.getTaskId()),
                    verifyTaskMessage.getProjectPath(),
                    verifyTaskMessage.getLlmConfig());
            taskActivityTracker.setSession(taskId, sessionId);
            for (LlmQuestionInfo questionInfo : verifyTaskMessage.getQuestionInfos()) {
                taskActivityTracker.touch(taskId);
                String llmResult = openCodeLLMClient.sendQuestionPrompt(
                        sessionId,
                        questionInfo,
                        verifyTaskMessage.getFilePath());
                verifyResultMessage.setWaitNum(verifyResultMessage.getWaitNum() - 1);
                verifyResultMessage.setQuestionId(questionInfo.getId());
                log.warn("{}结果为{}",questionInfo.getId(),llmResult);
                if (llmResult.contains("是")){
                    verifyResultMessage.setErrorNum(verifyResultMessage.getErrorNum() + 1);
                    verifyResultMessage.setIsQuestion(true);
                    log.info("{}为有问题",questionInfo.getId());
                }else {
                    verifyResultMessage.setAcNum(verifyResultMessage.getAcNum() + 1);
                    verifyResultMessage.setIsQuestion(false);
                    log.info("{}为没有问题",questionInfo.getId());
                }
                verifyQueuePublisher.sendFileResult(verifyResultMessage);
            }
            // 手动ACK
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("验证失败, taskId={}, fileId={}", verifyTaskMessage.getTaskId(), verifyTaskMessage.getFileId(), e);

            VerifyResultMessage errorResult = new VerifyResultMessage();
            errorResult.setTaskId(verifyTaskMessage.getTaskId());
            errorResult.setFileId(verifyTaskMessage.getFileId());
            errorResult.setFilePath(verifyTaskMessage.getFilePath());
            errorResult.setTotalNum(verifyTaskMessage.getQuestionInfos() != null ? verifyTaskMessage.getQuestionInfos().size() : 0);
            errorResult.setError(e.getMessage());
            verifyQueuePublisher.sendFileResult(errorResult);

            channel.basicAck(deliveryTag, false);
        } finally {
            taskActivityTracker.end(taskId);
        }
    }
}
