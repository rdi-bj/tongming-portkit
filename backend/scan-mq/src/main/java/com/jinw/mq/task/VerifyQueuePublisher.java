package com.jinw.mq.task;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VerifyQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendFileTask(VerifyTaskMessage verifyTaskMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.VERIFY_FILE_TASK_QUEUE,
                verifyTaskMessage
        );
    }

    public void sendFileResult(VerifyResultMessage verifyResultMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.VERIFY_FILE_RESULT_QUEUE,
                verifyResultMessage
        );
    }

    public void sendFileStart(VerifyStartMessage verifyStartMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.VERIFY_FILE_START_QUEUE,
                verifyStartMessage
        );
    }

}