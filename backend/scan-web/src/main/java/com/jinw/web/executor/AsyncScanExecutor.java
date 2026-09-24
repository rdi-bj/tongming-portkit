package com.jinw.web.executor;

import com.jinw.common.constant.ScanConstant;
import com.jinw.common.utils.ScanFileUtil;
import com.jinw.web.service.impl.statemachine.InMemoryScanStateMachine;
import com.jinw.web.service.impl.statemachine.ScanEvent;
import com.jinw.web.service.impl.statemachine.ScanStateMachine;
import com.jinw.web.config.exception.BusinessException;
import com.jinw.web.util.FileStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Component
@Slf4j
public class AsyncScanExecutor {

    @Autowired
    private ScanStateMachine scanStateMachine;

    @Async("scanTaskExecutor")
    public void executeAsyncScan(String taskId) {
        try {
            System.out.println(taskId);
            // 解压源码
            scanStateMachine.fire(taskId, ScanEvent.UNZIP);

            // 解压完成后，先初始化总文件数（支持并行流（Files.walk(path).parallel()））
            Path unzipPath = FileStorageUtil.resolve(taskId, InMemoryScanStateMachine.UNZIP_DIR);
            long fileNum;
            try (Stream<Path> stream = Files.walk(unzipPath)) {
                fileNum = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> {
                            String ext = ScanFileUtil.getFileExtension(
                                    path.getFileName().toString()
                            );
                            return ScanConstant.LANGUAGE_C_SUFFIX.contains(ext)
                                    || ScanConstant.LANGUAGE_CPP_SUFFIX.contains(ext)
                                    || ScanConstant.LANGUAGE_JAVA_SUFFIX.contains(ext)
                                    || "pom.xml".equals(path.getFileName().toString());
                        })
                        .filter(path -> {
                            try {
                                return Files.size(path) > 0;
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .count();
            }
            scanStateMachine.initScanProgress(taskId, fileNum);

            // 开始扫描
            scanStateMachine.fire(taskId, ScanEvent.START);
        } catch (BusinessException e) {
            log.error("业务异常, taskId={}, message={}", taskId, e.getMessage());
            scanStateMachine.fire(taskId, ScanEvent.ERROR);
        } catch (Exception e) {
            log.error("扫描任务异常, taskId={}", taskId, e);
            scanStateMachine.fire(taskId, ScanEvent.ERROR);
        }
    }
}