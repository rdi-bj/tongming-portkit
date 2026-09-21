package com.jinw.web.service.impl.statemachine.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinw.web.service.impl.statemachine.ScanProgress;
import com.jinw.web.service.impl.statemachine.ScanState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 每个 taskId 对应的 WebSocket 消息分发器
 * <p>
 * 核心职责：
 * 1. ScanState 变化 → 立即通知所有客户端
 * 2. ScanProgress 变化 → 前10次立即通知，后续进入缓冲队列，定时批量发送
 * 3. 任务结束时，将缓冲区的剩余消息全部发送完毕后再销毁连接
 */
@Data
@Slf4j
public class WebSocketMessageDispatcher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 前 N 次 ScanProgress 立即发送
     */
    private static final int IMMEDIATE_PROGRESS_LIMIT = 10;

    /**
     * 缓冲队列最大容量
     */
    private static final int MAX_BUFFER_SIZE = 100;

    /**
     * 关联的 taskId
     */
    private final String taskId;

    /**
     * 会话管理器
     */
    private final WebSocketSessionManager sessionManager;

    /**
     * ScanProgress 已立即发送的次数
     */
    private final AtomicInteger progressImmediateCount = new AtomicInteger(0);

    /**
     * 缓冲中的进度消息队列（只保留最新的一个，避免堆积）
     */
    private volatile ScanProgress bufferedProgress = null;

    /**
     * 缓冲定时器是否已启动
     */
    private volatile boolean bufferTimerStarted = false;

    /**
     * 任务是否已结束（SUCCESS / FAILED）
     */
    private volatile boolean taskFinished = false;

    /**
     * 是否已触发过状态变化通知（用于确保第一次状态变化一定被发送）
     */
    private volatile boolean initialStateSent = false;

    public WebSocketMessageDispatcher(String taskId, WebSocketSessionManager sessionManager) {
        this.taskId = taskId;
        this.sessionManager = sessionManager;
    }

    /**
     * 通知 ScanState 变化 —— 最高优先级，立即发送
     *
     * @param state 新的状态
     */
    public synchronized void notifyStateChange(ScanState state) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略状态变更通知: {}", taskId, state);
            return;
        }

        // 构建状态消息
        StateChangeMessage message = new StateChangeMessage(state.name());
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 立即通知状态变化: {}", taskId, state);
        }
    }

    /**
     * 通知 ScanProgress 变化
     * <p>
     * 前10次立即发送，后续进入缓冲模式
     *
     * @param progress 进度信息
     */
    public synchronized void notifyProgress(ScanProgress progress) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        int count = progressImmediateCount.get();

        if (count < IMMEDIATE_PROGRESS_LIMIT) {
            // 前10次：立即发送
            progressImmediateCount.incrementAndGet();
            ProgressMessage message = new ProgressMessage(progress);
            String json = toJson(message);
            if (json != null) {
                sessionManager.broadcast(taskId, json);
                log.debug("taskId={} 立即发送进度 ({}/{}): {}", taskId, count + 1, IMMEDIATE_PROGRESS_LIMIT, progress.getUnzipPercent());
            }
        } else {
            // 超过10次：放入缓冲区，启动定时发送
            this.bufferedProgress = progress;
            if (!bufferTimerStarted) {
                startBufferTimer();
            }
            log.debug("taskId={} 进度进入缓冲: {}", taskId, progress.getUnzipPercent());
        }
    }

    /**
     * 通知文件等待扫描
     *
     * @param fileName
     * @param filePath
     */
    public synchronized void notifyFileStateToWait(String fileName, String filePath) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.WATING);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件等待扫描: {}-{}", taskId, fileName, filePath);
        }
    }

    /**
     * 通知文件等待扫描
     *
     * @param fileName
     * @param filePath
     */
    public synchronized void notifyFileStateToVerifyWait(String fileName, String filePath, int totalNum) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.WATING, 0 , 0, totalNum, totalNum);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件等待验证: {}-{}", taskId, fileName, filePath);
        }
    }

    /**
     * 通知文件开始扫描
     *
     * @param fileName
     * @param filePath
     */
    public synchronized void notifyFileStateToScanf(String fileName, String filePath) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.SCANFING);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件开始扫描: {}-{}", taskId, fileName, filePath);
        }
    }

    /**
     * 通知文件开始验证
     *
     * @param fileName
     * @param filePath
     */
    public synchronized void notifyFileStateToVerify(String fileName, String filePath) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.VERIFYING);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件开始验证: {}-{}", taskId, fileName, filePath);
        }
    }

    /**
     * 通知文件结束扫描
     *
     * @param fileName
     * @param filePath
     * @param errorNum
     */
    public synchronized void notifyFileStateToComplete(String fileName, String filePath, int errorNum) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.COMPLETE, errorNum);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件扫描完成(错误：{}): {}-{}", taskId, errorNum, fileName, filePath);
        }
    }

    public synchronized void notifyFileStateToVerifyCompleteOrVerify(String fileName, String filePath, int acNum, int errorNum, int totalNum, int waitNum) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        // 构建状态消息
        FileStateChangeMessage message = new FileStateChangeMessage(
                fileName, filePath, waitNum == 0 ? FileState.COMPLETE : FileState.VERIFYING, acNum, errorNum, totalNum, waitNum);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件验证完成(错误：{}，误判：{}，问题数：{}，待验证：{}): {}-{}", taskId, errorNum, acNum, totalNum, waitNum, fileName, filePath);
        }
    }

    /**
     * 通知文件验证失败
     */
    public synchronized void notifyFileStateToError(String fileName, String filePath, String errorMsg) {
        if (taskFinished) {
            log.debug("taskId={} 任务已结束，忽略进度通知", taskId);
            return;
        }

        FileStateChangeMessage message = new FileStateChangeMessage(fileName, filePath, FileState.FAILED, errorMsg);
        String json = toJson(message);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            initialStateSent = true;
            log.debug("taskId={} 通知文件验证失败(error: {}): {}-{}", taskId, errorMsg, fileName, filePath);
        }
    }

    /**
     * 启动缓冲区定时发送任务
     * 每 500ms 检查一次缓冲区，有数据就发送
     */
    private synchronized void startBufferTimer() {
        if (bufferTimerStarted) {
            return;
        }
        bufferTimerStarted = true;

        Thread timerThread = new Thread(() -> {
            try {
                while (!taskFinished && bufferTimerStarted) {
                    ScanProgress latest = bufferedProgress;
                    if (latest != null) {
                        ProgressMessage message = new ProgressMessage(latest);
                        String json = toJson(message);
                        if (json != null) {
                            sessionManager.broadcast(taskId, json);
                            log.debug("taskId={} 定时发送缓冲进度: {}", taskId, latest.getUnzipPercent());
                        }
                        bufferedProgress = null;
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("taskId={} 缓冲定时器被中断", taskId);
            } finally {
                // 退出前，发送一次剩余缓冲
                ScanProgress remaining = bufferedProgress;
                if (remaining != null) {
                    ProgressMessage message = new ProgressMessage(remaining);
                    String json = toJson(message);
                    if (json != null) {
                        sessionManager.broadcast(taskId, json);
                    }
                    bufferedProgress = null;
                }
                bufferTimerStarted = false;
                log.debug("taskId={} 缓冲定时器已停止", taskId);
            }
        }, "ws-buffer-timer-" + taskId);

        timerThread.setDaemon(true);
        timerThread.start();
        log.debug("taskId={} 启动进度缓冲定时器", taskId);
    }

    /**
     * 标记任务结束，并发送最终消息后销毁连接
     *
     * @param state 最终状态
     */
    public synchronized void notifyTaskFinished(ScanState state) {
        if (taskFinished) {
            return;
        }
        taskFinished = true;

        // 停止缓冲定时器
        bufferTimerStarted = false;

        // 发送最终状态
        StateChangeMessage finalMessage = new StateChangeMessage(state.name());
        String json = toJson(finalMessage);

        if (json != null) {
            sessionManager.broadcast(taskId, json);
            log.debug("taskId={} 已发送最终状态: {}", taskId, state);
        }

        // 延迟关闭，确保消息送达
        sessionManager.destroyWithDelay(taskId, 1000);

        log.debug("taskId={} 任务结束流程完成，已安排延迟关闭WebSocket连接", taskId);
    }

    /**
     * 将对象转为 JSON 字符串
     */
    private String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("taskId={} JSON序列化失败: {}", taskId, e.getMessage());
            return null;
        }
    }

    // ============ 内部消息类 ============

    enum FileState {
        /**
         * 等待中
         */
        WATING,
        /**
         * 扫描中
         */
        SCANFING,
        /**
         * 验证中
         */
        VERIFYING,
        /**
         * 已完成
         */
        COMPLETE,
        /**
         * 验证失败
         */
        FAILED
    }

    /**
     * 状态变化消息
     */
    @Data
    public static class StateChangeMessage {
        private final String type = "STATE_CHANGE";
        private String state;

        public StateChangeMessage(String state) {
            this.state = state;
        }
    }

    /**
     * 进度变化消息
     */
    @Data
    public static class ProgressMessage {
        private final String type = "PROGRESS_UPDATE";
        private ScanProgress progress;

        public ProgressMessage(ScanProgress progress) {
            this.progress = progress;
        }
    }

    /**
     * 文件状态变化消息
     */
    @Data
    public static class FileStateChangeMessage {
        private final String type = "FILE_CHANGE";
        private String fileName;
        private String path;
        private String state;
        // 错误的数量(只有完成之后才会统计)
        private int errorNum;
        private int acNum;
        private int totalNum;
        private int waitNum;
        private String errorMsg;

        public FileStateChangeMessage(String fileName, String path, FileState state) {
            this.fileName = fileName;
            this.path = path;
            this.state = state.name();
        }

        public FileStateChangeMessage(String fileName, String path, FileState state, int errorNum) {
            this.fileName = fileName;
            this.path = path;
            this.state = state.name();
            this.errorNum = errorNum;
        }

        public FileStateChangeMessage(String fileName, String path, FileState state, int acNum, int errorNum, int totalNum, int waitNum) {
            this.acNum = acNum;
            this.errorNum = errorNum;
            this.fileName = fileName;
            this.path = path;
            this.state = state.name();
            this.totalNum = totalNum;
            this.waitNum = waitNum;
        }

        public FileStateChangeMessage(String fileName, String path, FileState state, String errorMsg) {
            this.fileName = fileName;
            this.path = path;
            this.state = state.name();
            this.errorMsg = errorMsg;
        }
    }
}
