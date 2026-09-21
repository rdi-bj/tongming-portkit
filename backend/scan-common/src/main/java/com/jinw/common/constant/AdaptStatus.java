package com.jinw.common.constant;

public enum AdaptStatus {

    /**
     * 准备适配
     */
    TODO,

    /**
     * 已进入队列
     */
    READY,

    /**
     * 正在适配问题
     */
    DOING_QUESTION,

    /**
     * 正在给出建议
     */
    DOING_SUGGEST,

    /**
     * 正在适配整个文件
     */
    DOING_FILE,

    /**
     * 完成
     */
    FINISH
}
