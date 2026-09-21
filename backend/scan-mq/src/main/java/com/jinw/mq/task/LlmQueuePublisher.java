package com.jinw.mq.task;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.LlmResultMessage;
import com.jinw.common.domain.LlmTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendFileTask(LlmTaskMessage llmTaskMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.LLM_FILE_TASK_QUEUE,
                llmTaskMessage
        );
    }

    public void sendFileResult(LlmResultMessage llmResultMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.LLM_FILE_RESULT_QUEUE,
                llmResultMessage
        );
    }

}