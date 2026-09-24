package com.jinw.worker.consumer;

import cn.hutool.json.JSONUtil;
import com.jinw.common.constant.AdaptStatus;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.LlmQuestionInfo;
import com.jinw.common.domain.LlmResultMessage;
import com.jinw.common.domain.LlmTaskMessage;
import com.jinw.common.domain.RiscvAnalysisResult;
import com.jinw.mq.task.LlmQueuePublisher;
import com.jinw.worker.llm.utils.OpenCodeLLMClient;
import com.jinw.worker.llm.utils.SchemeResult;
import com.jinw.worker.task.TaskActivityTracker;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmTaskConsumer {

    private final LlmQueuePublisher llmQueuePublisher;

    private final TaskActivityTracker taskActivityTracker;

    @Autowired
    private OpenCodeLLMClient openCodeLLMClient;

    public LlmTaskConsumer(LlmQueuePublisher llmQueuePublisher, TaskActivityTracker taskActivityTracker) {
        this.llmQueuePublisher = llmQueuePublisher;
        this.taskActivityTracker = taskActivityTracker;
    }

    @RabbitListener(
            queues = ScanConstant.LLM_FILE_TASK_QUEUE,
            containerFactory = "llmListenerContainerFactory"
    )
    public void consume(
            LlmTaskMessage llmTaskMessage,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String taskId = llmTaskMessage.getTaskId();
        taskActivityTracker.begin(taskId, llmTaskMessage.getSessionId());
        try {
            // 先更新配置
            Boolean haveLlmConfig = openCodeLLMClient.judgeHaveLlmConfig(llmTaskMessage.getLlmConfig());
            if (!haveLlmConfig){
                log.info("已更新大模型配置");
                openCodeLLMClient.updateLlmConfig(llmTaskMessage.getLlmConfig());
            }else {
                log.info("无需更新大模型配置");
            }
            LlmResultMessage llmResultMessage = new LlmResultMessage();
            llmResultMessage.setFileId(llmTaskMessage.getFileId());
            llmResultMessage.setTaskId(llmTaskMessage.getTaskId());
            llmResultMessage.setProjectPath(llmTaskMessage.getProjectPath());
            llmResultMessage.setQuestionFilePath(llmTaskMessage.getQuestionFilePath());
            String sessionId = openCodeLLMClient.getOrCreateSession(
                    llmTaskMessage.getSessionId(),
                    openCodeLLMClient.convertTaskIdToTitle("llm",llmTaskMessage.getTaskId()),
                    llmTaskMessage.getProjectPath(),
                    llmTaskMessage.getLlmConfig());
            taskActivityTracker.setSession(taskId, sessionId);
            llmResultMessage.setSessionId(sessionId);
            log.info("{} 开始问题AI适配:{}", llmTaskMessage.getTaskId(), llmTaskMessage.getQuestionFilePath());
            llmResultMessage.setStatus(AdaptStatus.DOING_QUESTION.name());
            for (LlmQuestionInfo llmQuestionInfo : llmTaskMessage.getQuestionInfoList()) {
                taskActivityTracker.touch(taskId);
                String llmResult = openCodeLLMClient.sendQuestionPrompt(
                        sessionId,
                        llmQuestionInfo);
                llmQuestionInfo.setDescription(llmResult);
                llmResultMessage.setLlmQuestionInfo(llmQuestionInfo);
                llmQueuePublisher.sendFileResult(llmResultMessage);
            }
            log.info("{} 结束问题AI适配:{}", llmTaskMessage.getTaskId(), llmTaskMessage.getQuestionFilePath());
            log.info("{} 开始文件AI适配:{}", llmTaskMessage.getTaskId(), llmTaskMessage.getQuestionFilePath());
            llmResultMessage.setLlmQuestionInfo(null);
            llmResultMessage.setStatus(AdaptStatus.DOING_SUGGEST.name());
            llmQueuePublisher.sendFileResult(llmResultMessage);
            taskActivityTracker.touch(taskId);
            RiscvAnalysisResult riscvAnalysisResult = openCodeLLMClient.sendFilePrompt(
                    sessionId,
                    openCodeLLMClient.buildAnalyzePrompt(llmTaskMessage.getQuestionFilePath()),
                    RiscvAnalysisResult.class);
            llmResultMessage.setRiscvAnalysisResult(riscvAnalysisResult);
            llmResultMessage.setStatus(AdaptStatus.DOING_FILE.name());
            llmQueuePublisher.sendFileResult(llmResultMessage);

            taskActivityTracker.touch(taskId);
            SchemeResult schemeResult;
            switch (riscvAnalysisResult.getSelectedScheme()) {
                case "A" -> {
                    String prompt = openCodeLLMClient.buildSchemeAPrompt(riscvAnalysisResult);
                    schemeResult = openCodeLLMClient.sendFilePrompt(sessionId, prompt, SchemeResult.class);
                }
                case "B" -> {
                    String prompt = openCodeLLMClient.buildSchemeBPrompt(riscvAnalysisResult);
                    schemeResult = openCodeLLMClient.sendFilePrompt(sessionId, prompt, SchemeResult.class);
                }
                case "C" -> {
                    String prompt = openCodeLLMClient.buildSchemeCPrompt(riscvAnalysisResult);
                    schemeResult = openCodeLLMClient.sendFilePrompt(sessionId, prompt, SchemeResult.class);
                }
                default -> throw new IllegalStateException(
                        "未知的适配方案: " + riscvAnalysisResult.getSelectedScheme());
            }

            log.info("{} 写入文件完成",schemeResult.getWrittenFilePath());
            log.info("{} 结束文件AI适配:{}", llmTaskMessage.getTaskId(), llmTaskMessage.getQuestionFilePath());
            llmResultMessage.setStatus(AdaptStatus.FINISH.name());
            llmQueuePublisher.sendFileResult(llmResultMessage);

            // 手动ACK
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {

            log.error("扫描失败", e);

            // 拒绝消息
            channel.basicNack(
                    deliveryTag,
                    false,
                    false
            );

        } finally {
            taskActivityTracker.end(taskId);
        }
    }


    private String extractJson(String content) {
        if (content == null) {
            return null;
        }

        content = content.trim();

        if (content.startsWith("```json")) {
            content = content.substring(7);
        }

        if (content.startsWith("```")) {
            content = content.substring(3);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }
}