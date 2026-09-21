package com.jinw.worker.consumer;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.AbortTaskMessage;
import com.jinw.worker.llm.utils.OpenCodeLLMClient;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AbortTaskConsumer {

    private final OpenCodeLLMClient openCodeLLMClient;

    public AbortTaskConsumer(OpenCodeLLMClient openCodeLLMClient) {
        this.openCodeLLMClient = openCodeLLMClient;
    }

    @RabbitListener(
            queues = ScanConstant.ABORT_TASK_QUEUE,
            containerFactory = "llmListenerContainerFactory"
    )
    public void consume(
            AbortTaskMessage abortTaskMessage,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String sessionId = abortTaskMessage.getSessionId();
            if (sessionId != null && !sessionId.isBlank()) {
                log.info("收到释放请求, taskId={}, sessionId={}",
                        abortTaskMessage.getTaskId(), sessionId);
                openCodeLLMClient.abortSession(sessionId);
            } else {
                log.info("收到释放请求但无 sessionId, taskId={}", abortTaskMessage.getTaskId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("释放 session 失败, taskId={}, sessionId={}",
                    abortTaskMessage.getTaskId(), abortTaskMessage.getSessionId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
