package com.jinw.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.AbortTaskMessage;
import com.jinw.mq.task.AbortQueuePublisher;
import com.jinw.web.domain.BTAppInfo;
import com.jinw.web.domain.BTQuestionFileInfo;
import com.jinw.web.domain.BTQuestionInfo;
import com.jinw.web.mapper.BTAppInfoMapper;
import com.jinw.web.mapper.BTQuestionFileInfoMapper;
import com.jinw.web.mapper.BTQuestionInfoMapper;
import com.jinw.web.service.BTAppInfoService;
import com.jinw.web.service.impl.statemachine.ScanState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class BTAppInfoServiceImpl extends ServiceImpl<BTAppInfoMapper, BTAppInfo> implements BTAppInfoService {

    private final BTAppInfoMapper mapper;
    private final BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    private final BTQuestionInfoMapper bTQuestionInfoMapper;
    private final AbortQueuePublisher abortQueuePublisher;
    private final AmqpAdmin amqpAdmin;

    @Override
    public String create(BTAppInfo appInfo) {
        mapper.insert(appInfo);
        return appInfo.getId();
    }

    @Override
    public String deleteById(String id) {
        BTAppInfo btAppInfo = mapper.selectById(id);
        if (btAppInfo == null) {
            return id;
        }

        // 检测中或 AI 适配中时，先释放检测队列、检测任务以及 AI 适配线程
        releaseIfBusy(btAppInfo);

        String taskId = btAppInfo.getTaskId();
        if (taskId != null){
            bTQuestionFileInfoMapper.delete(
                    new QueryWrapper<BTQuestionFileInfo>().eq("TASK_ID", taskId)
            );
            bTQuestionInfoMapper.delete(
                    new QueryWrapper<BTQuestionInfo>().eq("TASK_ID", taskId)
            );
        }
        mapper.deleteById(id);
        return id;
    }

    /**
     * 判断项目是否处于检测中或 AI 适配中，若是则释放相关资源
     */
    private void releaseIfBusy(BTAppInfo btAppInfo) {
        boolean detecting = isDetecting(btAppInfo.getStatus());
        boolean adapting = "1".equals(btAppInfo.getAdaptStatus());
        if (!detecting && !adapting) {
            return;
        }

        // 存在 sessionId 时通知 worker 中止 opencode session，从而释放 AI 适配线程
        if (StringUtils.hasText(btAppInfo.getSessionId())) {
            AbortTaskMessage abortTaskMessage = new AbortTaskMessage();
            abortTaskMessage.setTaskId(btAppInfo.getTaskId());
            abortTaskMessage.setSessionId(btAppInfo.getSessionId());
            abortQueuePublisher.sendAbortTask(abortTaskMessage);
            log.info("项目 {} 删除，已发送释放请求, taskId={}, sessionId={}",
                    btAppInfo.getId(), btAppInfo.getTaskId(), btAppInfo.getSessionId());
        }

        purgeQueues();
    }

    private boolean isDetecting(String status) {
        if (status == null) {
            return false;
        }
        return ScanState.SCANNING.name().equals(status)
                || ScanState.AI_VERIFY.name().equals(status);
    }

    private void purgeQueues() {
        try {
            amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_TASK_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_RESULT_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_START_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.LLM_FILE_TASK_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.LLM_FILE_RESULT_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_TASK_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_RESULT_QUEUE, false);
            amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_START_QUEUE, false);
            log.info("删除项目，已清空检测与适配相关队列");
        } catch (Exception e) {
            log.error("清空队列失败", e);
        }
    }

    @Override
    public String update(BTAppInfo appInfo) {
        mapper.updateById(appInfo);
        return appInfo.getId();
    }

    @Override
    public BTAppInfo getById(String id) {
        return mapper.selectById(id);
    }


}