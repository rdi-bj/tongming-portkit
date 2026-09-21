package com.jinw.mq.task;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.ScanResultMessage;
import com.jinw.common.domain.ScanStartMessage;
import com.jinw.common.domain.ScanTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScanQueuePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sendFileTask(ScanTaskMessage scanTaskMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.SCAN_FILE_TASK_QUEUE,
                scanTaskMessage
        );
    }

    public void sendFileResult(ScanResultMessage scanResultMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.SCAN_FILE_RESULT_QUEUE,
                scanResultMessage
        );
    }

    public void sendFileStart(ScanStartMessage scanStartMessage) {

        rabbitTemplate.convertAndSend(
                ScanConstant.SCAN_FILE_START_QUEUE,
                scanStartMessage
        );
    }

}