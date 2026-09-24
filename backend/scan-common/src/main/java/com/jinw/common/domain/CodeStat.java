package com.jinw.common.domain;

import lombok.Getter;

@Getter
public class CodeStat {

    /**
     * 文件总行数（\n 数量）
     */
    private long totalLines;

    /**
     * 空行数（仅包含空白字符的行）
     */
    private long emptyLines;

    /**
     * 注释行数（被 AST 注释节点覆盖的行）
     */
    private long commentLines;

    /**
     * 有效代码行数（非空、非注释）
     */
    private long codeLines;

    // 临时状态（避免重复计算）
    /**
     * 当前正在处理的行号（1-based）
     */
    private int lastLine = -1;

    /**
     * 当前行是否存在代码节点
     */
    private boolean lineHasCode;

    /**
     * 当前行是否存在注释节点
     */
    private boolean lineHasComment;

    public CodeStat() {
    }

    // ================== 行事件处理 ==================

    /**
     * 通知统计器进入某一行。
     * <p>
     * 如果行号发生变化，会自动结算上一行的统计结果。
     *
     * @param line 当前行号（1-based）
     */
    public void recordLine(int line) {
        if (line == lastLine) {
            return;
        }
        flush();
        lastLine = line;
    }

    /**
     * 结算当前行的统计结果。
     * <p>
     * 该方法必须在以下时机调用：
     * <ul>
     *   <li>切换到新的一行</li>
     *   <li>文件扫描结束时</li>
     * </ul>
     */
    private void flush() {
        if (lastLine < 0) {
            return;
        }

        totalLines++;

        if (!lineHasCode && !lineHasComment) {
            emptyLines++;
        } else if (lineHasComment && !lineHasCode) {
            commentLines++;
        } else {
            codeLines++;
        }

        lineHasCode = false;
        lineHasComment = false;
    }

    /**
     * 标记当前行包含代码节点。
     */
    public void markCode() {
        lineHasCode = true;
    }

    /**
     * 标记当前行包含注释节点。
     */
    public void markComment() {
        lineHasComment = true;
    }

    /**
     * 结束统计（必须调用）。
     * <p>
     * 用于确保最后一行被正确结算。
     */
    public void end() {
        flush();
    }

    /**
     * 强制补齐缺失的行（用于空行兜底）
     */
    public void ensureLineCount(long actualTotalLines) {
        while (totalLines < actualTotalLines) {
            totalLines++;
            emptyLines++;
        }
    }
}