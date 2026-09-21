package com.jinw.worker.consumer;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.CodeLine;
import com.jinw.common.domain.ScanResultMessage;
import com.jinw.common.domain.ScanStartMessage;
import com.jinw.common.domain.ScanTaskMessage;
import com.jinw.common.domain.graph.ProjectCallGraph;
import com.jinw.common.domain.graph.dto.ProjectCallGraphDTO;
import com.jinw.mq.task.ScanQueuePublisher;
import com.jinw.worker.ast.TreeSitterUtil;
import com.jinw.worker.task.TaskActivityTracker;
import com.jinw.worker.util.CodeLineExtractor;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Component
public class ScanTaskConsumer {

    private final ScanQueuePublisher scanTaskPublisher;

    private final TaskActivityTracker taskActivityTracker;

    public ScanTaskConsumer(ScanQueuePublisher scanTaskPublisher, TaskActivityTracker taskActivityTracker) {
        this.scanTaskPublisher = scanTaskPublisher;
        this.taskActivityTracker = taskActivityTracker;
    }

    @RabbitListener(queues = ScanConstant.SCAN_FILE_TASK_QUEUE)
    public void consume(
            ScanTaskMessage scanTaskMessage,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        taskActivityTracker.begin(scanTaskMessage.getTaskId(), null);
        try {
            ScanStartMessage scanStartMessage = new ScanStartMessage();
            scanStartMessage.setTaskId(scanTaskMessage.getTaskId());
            scanStartMessage.setFilePath(scanTaskMessage.getFilePath());
            scanTaskPublisher.sendFileStart(scanStartMessage);
            ScanResultMessage scanResultMessage = new ScanResultMessage();
            if (ScanConstant.LANGUAGE_JAVA.equals(scanTaskMessage.getLanguage())){
                List<CodeLine> codeLines = CodeLineExtractor.extract(Path.of(scanTaskMessage.getFilePath()));
                scanResultMessage.setTaskId(scanTaskMessage.getTaskId());
                scanResultMessage.setCodeLines(codeLines);
                scanResultMessage.setFilePath(scanTaskMessage.getFilePath());
                scanResultMessage.setLanguage(scanTaskMessage.getLanguage());
            }else {
                ProjectCallGraph graph = TreeSitterUtil.parse(scanTaskMessage);
                ProjectCallGraphDTO projectCallGraphDTO = graph.convert2DTO();
                scanResultMessage.setTaskId(scanTaskMessage.getTaskId());
                scanResultMessage.setGraph(projectCallGraphDTO);
                scanResultMessage.setFilePath(scanTaskMessage.getFilePath());
                scanResultMessage.setLanguage(scanTaskMessage.getLanguage());
            }

            scanTaskPublisher.sendFileResult(scanResultMessage);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("扫描失败, taskId={}, filePath={}", scanTaskMessage.getTaskId(), scanTaskMessage.getFilePath(), e);

            try {
                ScanResultMessage scanResultMessage = new ScanResultMessage();
                scanResultMessage.setTaskId(scanTaskMessage.getTaskId());
                scanResultMessage.setGraph(new ProjectCallGraphDTO());
                scanResultMessage.setFilePath(scanTaskMessage.getFilePath());
                scanTaskPublisher.sendFileResult(scanResultMessage);
            } catch (Exception sendEx) {
                log.error("发送失败结果也失败, taskId={}", scanTaskMessage.getTaskId(), sendEx);
            }

            channel.basicAck(deliveryTag, false);
        } finally {
            taskActivityTracker.end(scanTaskMessage.getTaskId());
        }
    }
}