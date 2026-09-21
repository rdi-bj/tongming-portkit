package com.jinw.web.consumer;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.VerifyResultMessage;
import com.jinw.common.domain.VerifyStartMessage;
import com.jinw.web.domain.BTQuestionFileInfo;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.mapper.BTQuestionFileInfoMapper;
import com.jinw.web.mapper.BTQuestionInfoMapper;
import com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine;
import com.jinw.web.service.impl.statemachine.ScanEvent;
import com.jinw.web.service.impl.statemachine.ScanStateMachine;
import com.jinw.web.service.impl.statemachine.ws.WebSocketMessageDispatcher;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.util.FileStorageUtil;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Slf4j
@Component
public class VerifyResultConsumer {

    private final ScanStateMachine scanStateMachine;
    private final BTQuestionInfoMapper bTQuestionInfoMapper;
    private final BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    private final AmqpAdmin amqpAdmin;

    public VerifyResultConsumer(ScanStateMachine scanStateMachine,
                                BTQuestionInfoMapper bTQuestionInfoMapper,
                                BTQuestionFileInfoMapper bTQuestionFileInfoMapper,
                                AmqpAdmin amqpAdmin) {
        this.scanStateMachine = scanStateMachine;
        this.bTQuestionInfoMapper = bTQuestionInfoMapper;
        this.bTQuestionFileInfoMapper = bTQuestionFileInfoMapper;
        this.amqpAdmin = amqpAdmin;
    }

    @RabbitListener(queues = ScanConstant.VERIFY_FILE_START_QUEUE)
    public void onStartVerify(
            VerifyStartMessage result,
            Message message,
            Channel channel
    ) throws Exception {
        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();
        channel.basicAck(deliveryTag, false);

        WebSocketMessageDispatcher dispatcher = scanStateMachine.getDispatcher(result.getTaskId());
        File file = new File(result.getFilePath());
        dispatcher.notifyFileStateToVerify(file.getName(), FileStorageUtil.toRelativePath(result.getTaskId() + File.separator + InMemoryScanStateMachine.UNZIP_DIR, result.getFilePath()));
    }

    @RabbitListener(
            queues = ScanConstant.VERIFY_FILE_RESULT_QUEUE,
            containerFactory = "llmListenerContainerFactory")
    public void consume(
            VerifyResultMessage result,
            Message message,
            Channel channel
    ) throws Exception {

        long deliveryTag =
                message.getMessageProperties().getDeliveryTag();

        try {
            if (result.getError() != null) {
                log.error("验证任务异常, taskId={}, fileId={}, error={}", result.getTaskId(), result.getFileId(), result.getError());
                channel.basicAck(deliveryTag, false);
                purgeVerifyQueues();
                scanStateMachine.fire(result.getTaskId(), ScanEvent.ERROR);
                return;
            }

            if (!result.getIsQuestion()){
                BTQuestionInfo btQuestionInfo = bTQuestionInfoMapper.selectById(result.getQuestionId());
                btQuestionInfo.setAiVerifyStatus("0");
                bTQuestionInfoMapper.updateById(btQuestionInfo);
                log.info("id为{}的问题被AI标记为没有问题",result.getQuestionId());
            }

            if (result.getWaitNum() == 0 && result.getErrorNum() == 0){
                BTQuestionFileInfo btQuestionFileInfo = bTQuestionFileInfoMapper.selectById(result.getFileId());
                btQuestionFileInfo.setAiVerifyStatus("0");
                bTQuestionFileInfoMapper.updateById(btQuestionFileInfo);
                log.info("id为{}的文件被AI标记为没有问题",result.getFileId());
            }

            scanStateMachine.updateVerifyProgress(
                    result.getTaskId(),
                    result.getFilePath(),
                    result.getErrorNum(),
                    result.getAcNum(),
                    result.getTotalNum(),
                    result.getWaitNum());
            channel.basicAck(deliveryTag, false);
            if (scanStateMachine.isVerifyFinished(result.getTaskId())) {
                scanStateMachine.fire(result.getTaskId(), ScanEvent.AI_FINISH);
                log.info("任务完成: {}", result.getTaskId());
            }
        } catch (BusinessException e) {
            log.error("业务异常, taskId={}, message={}", result != null ? result.getTaskId() : null, e.getMessage());
            channel.basicAck(deliveryTag, false);
            purgeVerifyQueues();
            scanStateMachine.fire(result != null ? result.getTaskId() : null, ScanEvent.ERROR);
        } catch (Exception e) {

            log.error("消费失败", e);
            channel.basicNack(
                    deliveryTag,
                    false,
                    true
            );
        }
    }

    private void purgeVerifyQueues() {
        try {
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_TASK_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_RESULT_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_START_QUEUE, false);
            log.info("已清空验证相关队列");
        } catch (Exception e) {
            log.error("清空验证队列失败", e);
        }
    }
}
