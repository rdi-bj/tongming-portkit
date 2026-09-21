package com.jinw.worker.util;

import com.jinw.common.domain.CodeLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 提取 Java 源文件 / pom.xml 中非注释代码段的行列信息
 */
public class CodeLineExtractor {

    private CodeLineExtractor() {
    }

    /**
     * 传入 .java 或 .xml（pom.xml）文件，返回所有非注释代码段的位置信息
     * 行号、起始列、结束列均为 0-based，结束列不含
     */
    public static List<com.jinw.common.domain.CodeLine> extract(Path file) throws IOException {
        String name = file.getFileName().toString().toLowerCase();
        String content = Files.readString(file);
        if (name.endsWith(".java")) {
            return extractJava(content);
        }
        if (name.endsWith(".xml")) {
            return extractXml(content);
        }
        throw new IllegalArgumentException("Unsupported file type: " + file);
    }

    /**
     * 提取 Java 源码中的非注释代码段，支持 //、/* *​/、/** *​/ 及字符串、字符、文本块内的注释符号
     */
    public static List<com.jinw.common.domain.CodeLine> extractJava(String source) {
        List<int[]> commentRanges = new ArrayList<>();
        int n = source.length();
        int i = 0;
        while (i < n) {
            char c = source.charAt(i);
            if (c == '"') {
                if (i + 2 < n && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                    int j = i + 3;
                    while (j + 2 < n) {
                        if (source.charAt(j) == '"' && source.charAt(j + 1) == '"' && source.charAt(j + 2) == '"') {
                            j += 3;
                            break;
                        }
                        j++;
                    }
                    i = j;
                } else {
                    int j = i + 1;
                    while (j < n) {
                        char ch = source.charAt(j);
                        if (ch == '\\') {
                            j += 2;
                            continue;
                        }
                        if (ch == '"') {
                            j++;
                            break;
                        }
                        j++;
                    }
                    i = j;
                }
            } else if (c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char ch = source.charAt(j);
                    if (ch == '\\') {
                        j += 2;
                        continue;
                    }
                    if (ch == '\'') {
                        j++;
                        break;
                    }
                    j++;
                }
                i = j;
            } else if (c == '/' && i + 1 < n) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    int start = i;
                    int j = i + 2;
                    while (j < n && source.charAt(j) != '\n' && source.charAt(j) != '\r') {
                        j++;
                    }
                    commentRanges.add(new int[]{start, j});
                    i = j;
                } else if (next == '*') {
                    int start = i;
                    int j = i + 2;
                    while (j + 1 < n && !(source.charAt(j) == '*' && source.charAt(j + 1) == '/')) {
                        j++;
                    }
                    int end = j + 1 < n ? j + 2 : n;
                    commentRanges.add(new int[]{start, end});
                    i = end;
                } else {
                    i++;
                }
            } else {
                i++;
            }
        }
        return buildCodeLines(source, commentRanges);
    }

    /**
     * 提取 XML（pom.xml）中的非注释代码段，支持 <!-- --> 注释
     */
    public static List<com.jinw.common.domain.CodeLine> extractXml(String source) {
        List<int[]> commentRanges = new ArrayList<>();
        int n = source.length();
        int i = 0;
        while (i + 3 < n) {
            if (source.charAt(i) == '<' && source.charAt(i + 1) == '!'
                    && source.charAt(i + 2) == '-' && source.charAt(i + 3) == '-') {
                int start = i;
                int j = source.indexOf("-->", i + 4);
                int end = j >= 0 ? j + 3 : n;
                commentRanges.add(new int[]{start, end});
                i = end;
            } else {
                i++;
            }
        }
        return buildCodeLines(source, commentRanges);
    }

    private static List<com.jinw.common.domain.CodeLine> buildCodeLines(String source, List<int[]> commentRanges) {
        commentRanges.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        List<com.jinw.common.domain.CodeLine> result = new ArrayList<>();
        int n = source.length();
        int line = 0;
        int lineStart = 0;
        int idx = 0;
        while (true) {
            int lineEnd = lineStart;
            while (lineEnd < n && source.charAt(lineEnd) != '\n' && source.charAt(lineEnd) != '\r') {
                lineEnd++;
            }
            while (idx < commentRanges.size() && commentRanges.get(idx)[1] <= lineStart) {
                idx++;
            }
            int pos = lineStart;
            while (idx < commentRanges.size() && commentRanges.get(idx)[0] < lineEnd) {
                int[] r = commentRanges.get(idx);
                if (r[0] > pos) {
                    addSegment(result, source, line, lineStart, pos, r[0]);
                }
                if (r[1] >= lineEnd) {
                    pos = lineEnd;
                    break;
                }
                pos = r[1];
                idx++;
            }
            if (pos < lineEnd) {
                addSegment(result, source, line, lineStart, pos, lineEnd);
            }
            line++;
            if (lineEnd >= n) {
                break;
            }
            if (source.charAt(lineEnd) == '\r' && lineEnd + 1 < n && source.charAt(lineEnd + 1) == '\n') {
                lineStart = lineEnd + 2;
            } else {
                lineStart = lineEnd + 1;
            }
        }
        return result;
    }

    private static void addSegment(List<com.jinw.common.domain.CodeLine> result, String source, int line, int lineStart, int start, int end) {
        while (start < end && isWhitespace(source.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(source.charAt(end - 1))) {
            end--;
        }
        if (start >= end) {
            return;
        }
        result.add(new CodeLine(line, start - lineStart, end - lineStart, source.substring(start, end)));
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t';
    }
}
