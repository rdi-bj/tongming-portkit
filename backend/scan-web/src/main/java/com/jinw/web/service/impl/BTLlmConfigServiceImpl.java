package com.jinw.web.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jinw.common.constant.AdaptStatus;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.LlmConfig;
import com.jinw.common.domain.LlmQuestionInfo;
import com.jinw.common.domain.LlmTaskMessage;
import com.jinw.common.domain.RiscvAnalysisResult;
import com.jinw.common.utils.ScanFileUtil;
import com.jinw.mq.task.LlmQueuePublisher;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.domain.*;
import com.jinw.web.mapper.*;
import com.jinw.web.service.BTLlmConfigService;
import com.jinw.web.util.FileStorageUtil;
import com.jinw.web.util.LlmConfigValidator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine.UNZIP_DIR;

@Service
@RequiredArgsConstructor
public class BTLlmConfigServiceImpl extends ServiceImpl<BTLlmConfigMapper, BTLlmConfig> implements BTLlmConfigService {

    private final BTLlmConfigMapper btLlmConfigMapper;
    private final LlmQueuePublisher llmQueuePublisher;
    private final AmqpAdmin amqpAdmin;
    private final BTLlmResultMapper bTLlmResultMapper;
    private final BTQuestionFileInfoMapper bTQuestionFileInfoMapper;
    private final BTQuestionInfoMapper bTQuestionInfoMapper;
    private final BTAppInfoMapper bTAppInfoMapper;

    @PostConstruct
    public void init(){
        amqpAdmin.purgeQueue(ScanConstant.LLM_FILE_RESULT_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.LLM_FILE_TASK_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_RESULT_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_START_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.VERIFY_FILE_TASK_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_TASK_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_RESULT_QUEUE, false);
        amqpAdmin.purgeQueue(ScanConstant.SCAN_FILE_START_QUEUE, false);
        // 批量重置未完成适配的任务状态，避免逐条查询导致启动缓慢
        List<BTQuestionFileInfo> unfinishedFiles = bTQuestionFileInfoMapper.selectList(
                new QueryWrapper<BTQuestionFileInfo>()
                        .ne("STATUS", AdaptStatus.FINISH.name())
                        .select("ID", "TASK_ID"));
        List<String> unfinishedFileIds = unfinishedFiles.stream()
                .map(BTQuestionFileInfo::getId)
                .collect(Collectors.toList());
        if (!unfinishedFileIds.isEmpty()) {
            // 将未适配完成文件下未结束的问题状态重置
            bTQuestionInfoMapper.update(null, new UpdateWrapper<BTQuestionInfo>()
                    .in("FILE_ID", unfinishedFileIds)
                    .ne("STATUS", AdaptStatus.FINISH.name())
                    .set("STATUS", AdaptStatus.TODO.name()));
            // 将未适配完成的项目适配状态重置
            List<String> unfinishedTaskIds = unfinishedFiles.stream()
                    .map(BTQuestionFileInfo::getTaskId)
                    .distinct()
                    .collect(Collectors.toList());
            bTAppInfoMapper.update(null, new UpdateWrapper<BTAppInfo>()
                    .in("TASK_ID", unfinishedTaskIds)
                    .ne("ADAPT_STATUS", "-1")
                    .set("ADAPT_STATUS", "0"));
        }
        // 将等待开始适配的文件状态重置为待适配
        bTQuestionFileInfoMapper.update(null, new UpdateWrapper<BTQuestionFileInfo>()
                .eq("STATUS", AdaptStatus.READY.name())
                .set("STATUS", AdaptStatus.TODO.name()));
    }

    @Override
    public String create(BTLlmConfig btLlmConfig) {
        btLlmConfigMapper.insert(btLlmConfig);
        return btLlmConfig.getId();
    }

    @Override
    public String deleteById(String id) {
        btLlmConfigMapper.deleteById(id);
        return id;
    }

    @Override
    public String update(BTLlmConfig btLlmConfig) {
        btLlmConfigMapper.updateById(btLlmConfig);
        return btLlmConfig.getId();
    }

    @Override
    public BTLlmConfig getById(String id) {
        return btLlmConfigMapper.selectById(id);
    }

    @Override
    public String adaptFile(String fileId) {
        // 适配前先校验配置模型是否可用
        checkEnabledLlmConfig();
        BTQuestionFileInfo btQuestionFileInfo = bTQuestionFileInfoMapper.selectById(fileId);
        if (AdaptStatus.READY.name().equals(btQuestionFileInfo.getStatus())){
            throw new BusinessException("该文件已准备开始适配，请勿重复提交适配请求！");
        }
        llmQueuePublisher.sendFileTask(buildLlmTaskMessage(btQuestionFileInfo));
        // 入队列成功修改文件状态
        btQuestionFileInfo.setStatus(AdaptStatus.READY.name());
        bTQuestionFileInfoMapper.updateById(btQuestionFileInfo);
        return fileId;
    }

    /**
     * 校验当前启用的模型是否可用，不可用时抛出业务异常
     */
    private void checkEnabledLlmConfig() {
        BTLlmConfig btLlmConfig = btLlmConfigMapper.selectOne(new QueryWrapper<BTLlmConfig>().eq("ENABLED", "1"));
        LlmConfigValidator.validate(btLlmConfig);
    }

    private LlmTaskMessage buildLlmTaskMessage(BTQuestionFileInfo btQuestionFileInfo){
        // 构造LlmTaskMessage
        String fileId = btQuestionFileInfo.getId();
        String taskId = btQuestionFileInfo.getTaskId();
        String questionFilePath = btQuestionFileInfo.getFilePath();
        String absolutePath = FileStorageUtil.toAbsolutePath(taskId + File.separator + UNZIP_DIR, questionFilePath);
        LlmTaskMessage llmTaskMessage = new LlmTaskMessage();
        llmTaskMessage.setTaskId(taskId);
        llmTaskMessage.setFileId(fileId);
        llmTaskMessage.setProjectPath(FileStorageUtil.toAbsolutePath("", taskId));
        llmTaskMessage.setQuestionFilePath(absolutePath);
        // 取出所有没有结束的问题并按行数升序排序
        List<BTQuestionInfo> btQuestionInfos = bTQuestionInfoMapper.selectList(new QueryWrapper<BTQuestionInfo>()
                .eq("FILE_ID", fileId)
                .eq("AI_VERIFY_STATUS","1")
                .eq("HIT_KNOWLEDGE","1")
                .eq("HAVE_ADAPT","1")
                .ne("STATUS", AdaptStatus.FINISH.name()));
        List<LlmQuestionInfo> llmQuestionInfos =
                btQuestionInfos.stream()
                        .sorted(Comparator.comparingInt(BTQuestionInfo::getStartLine))
                        .map(q -> {
                            LlmQuestionInfo info = new LlmQuestionInfo();
                            info.setId(q.getId());
                            info.setText(q.getText());
                            info.setDescription(q.getDescription());
                            return info;
                        }).toList();
        llmTaskMessage.setQuestionInfoList(llmQuestionInfos);
        // 获取启用配置
        BTAppInfo btAppInfo = bTAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", taskId));
        llmTaskMessage.setSessionId(btAppInfo.getSessionId());
        BTLlmConfig btLlmConfig = btLlmConfigMapper.selectOne(new QueryWrapper<BTLlmConfig>().eq("ENABLED", "1"));
        LlmConfig llmConfig = new LlmConfig();
        BeanUtil.copyProperties(btLlmConfig, llmConfig);
        llmTaskMessage.setLlmConfig(llmConfig);
        return llmTaskMessage;
    }

    @Override
    public LlmQuestionResult fileAdaptResult(String fileId) {
        LlmQuestionResult llmQuestionResult = new LlmQuestionResult();
        BTQuestionFileInfo btQuestionFileInfo = bTQuestionFileInfoMapper.selectById(fileId);
        BeanUtil.copyProperties(btQuestionFileInfo, llmQuestionResult);
        // 这里FINISH的时候返回文件内容
        if (AdaptStatus.FINISH.name().equals(llmQuestionResult.getStatus())){
            RiscvAnalysisResult riscvAnalysisResult = JSONUtil.toBean(llmQuestionResult.getAdaptResult(), RiscvAnalysisResult.class);
            String absolutePath = FileStorageUtil.toAbsolutePath(btQuestionFileInfo.getTaskId(), riscvAnalysisResult.getSuggestedRiscvFilePath());
            llmQuestionResult.setAdaptText(ScanFileUtil.readFileContent(absolutePath));
        }
        List<BTQuestionInfo> btQuestionInfos = bTQuestionInfoMapper.selectList(new QueryWrapper<BTQuestionInfo>()
                .eq("FILE_ID", fileId)
                .eq("AI_VERIFY_STATUS","1")
                .eq("HIT_KNOWLEDGE","1")
                .eq("HAVE_ADAPT","1"));
        llmQuestionResult.setBtQuestionInfos(btQuestionInfos);
        return llmQuestionResult;
    }

    @Override
    public String changeLlmConfig(String id) {
        List<BTLlmConfig> btLlmConfigs = btLlmConfigMapper.selectList(null);
        for (BTLlmConfig btLlmConfig : btLlmConfigs) {
            if (id.equals(btLlmConfig.getId())){
                btLlmConfig.setEnabled("1");
            }else {
                btLlmConfig.setEnabled("0");
            }
            btLlmConfigMapper.updateById(btLlmConfig);
        }
        return id;
    }

    @Override
    public String adaptProject(String taskId) {
        // 批量适配前先校验配置模型是否可用
        checkEnabledLlmConfig();
        List<BTQuestionFileInfo> btQuestionFileInfos = bTQuestionFileInfoMapper.selectList(new QueryWrapper<BTQuestionFileInfo>()
                .eq("TASK_ID", taskId)
                .eq("STATUS", AdaptStatus.TODO.name())
                .eq("AI_VERIFY_STATUS","1")
                .eq("HIT_KNOWLEDGE","1")
                .eq("HAVE_ADAPT","1"));
        for (BTQuestionFileInfo btQuestionFileInfo : btQuestionFileInfos) {
            llmQueuePublisher.sendFileTask(buildLlmTaskMessage(btQuestionFileInfo));
            // 入队列成功修改文件状态
            btQuestionFileInfo.setStatus(AdaptStatus.READY.name());
            bTQuestionFileInfoMapper.updateById(btQuestionFileInfo);
        }
        BTAppInfo btAppInfo = bTAppInfoMapper.selectOne(new QueryWrapper<BTAppInfo>().eq("TASK_ID", taskId));
        btAppInfo.setAdaptStatus("1");
        bTAppInfoMapper.updateById(btAppInfo);
        return taskId;
    }

    public boolean hasMessage(String queueName) {
        Properties props = amqpAdmin.getQueueProperties(queueName);
        if (props == null) {
            return false;
        }

        Object count = props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        if (count instanceof Number) {
            return ((Number) count).intValue() > 0;
        }
        return false;
    }
}
