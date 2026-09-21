package com.jinw.mq.task;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.AbortTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AbortQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendAbortTask(AbortTaskMessage abortTaskMessage) {
        rabbitTemplate.convertAndSend(
                ScanConstant.ABORT_TASK_QUEUE,
                abortTaskMessage
        );
    }

}
