package com.jinw.web.service.impl.statemachine;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 扫描进度
 */
@Data
public class ScanProgress {

    /**
     * 总文件数
     */
    private long total;

    /**
     * 总行数
     */
    private long totalRow;

    /**
     * 已扫描文件数
     */
    private final AtomicLong completed = new AtomicLong(0);

    /**
     * 开始时间
     */
    private long startTime;

    /**
     * 结束时间
     */
    private long endTime;

    /**
     * zip所在路径
     **/
    private String relativePath;


    /**
     * zip解压进度
     */
    private int unzipPercent;

    /**
     * 已验证文件数
     */
    private AtomicLong alreadyVerifyFileCount = new AtomicLong(0);

    /**
     * 总需验证文件数
     */
    private long totalVerifyFileCount;

    /**
     * 获取检测进度
     *
     * @return
     */
    public int getProgressPercent() {
        if (total <= 0) {
            return 0;
        }
        return (int) ((completed.get() * 100.0) / total);
    }

    /**
     * 添加已完成的文件数量
     *
     * @return
     */
    public long addCompleted() {
        return completed.incrementAndGet();
    }

    /**
     * 添加已完成的文件数量
     *
     * @return
     */
    public long addVerifyCompleted() {
        return alreadyVerifyFileCount.incrementAndGet();
    }

    /**
     * 添加已扫描的代码行数
     *
     * @param totalRow
     * @return
     */
    public long addTotalRow(long totalRow) {
        this.totalRow += totalRow;
        return this.totalRow;
    }
}