package com.jinw.web.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;

@Data
public class ScanProgress {

    /**
     * 总文件数
     */
    @Schema(description = "总文件数")
    private int total;

    /**
     * 已完成
     */
    @Schema(description = "已完成文件数")
    private AtomicInteger completed = new AtomicInteger(0);

    /**
     * 状态
     */
    @Schema(description = "状态")
    private String status;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    private long startTime;

}