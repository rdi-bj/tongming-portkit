package com.jinw.common.utils;

import com.jinw.common.constant.FileCategory;
import com.jinw.common.constant.ScanConstant;
import com.jinw.common.domain.LineStat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * 文件扫描工具类
 * <p>
 * 提供文件扩展名解析、文件类型分类、代码行数统计等通用能力，
 * 常用于代码仓库扫描、统计分析场景。
 * </p>
 */
public class ScanFileUtil {

    /**
     * 获取文件扩展名（不包含点号）
     *
     * @param fileName 文件名
     * @return 文件扩展名（如 .java / .xml），若无扩展名则返回空字符串
     */
    public static String getFileExtension(String fileName) {

        int index = fileName.lastIndexOf(".");
        if (index == -1 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index).toLowerCase();
    }

    /**
     * 根据文件名或扩展名判断文件所属分类
     *
     * <p>匹配优先级：</p>
     * <ol>
     *   <li>优先按完整文件名匹配（如 pom.xml）</li>
     *   <li>其次按文件扩展名匹配</li>
     *   <li>未匹配到则返回 {@link FileCategory#OTHER}</li>
     * </ol>
     *
     * @param file 待识别的文件
     * @return 文件分类枚举 {@link FileCategory}
     */
    public static FileCategory getFileCategory(File file) {

        String fileName = file.getName().toLowerCase();

        // 优先按文件名匹配
        FileCategory category =
                ScanConstant.FILE_NAME_CATEGORY_MAP.get(fileName);
        if (category != null) {
            return category;
        }

        // 按扩展名匹配
        String ext = getFileExtension(fileName);
        return ScanConstant.EXTENSION_CATEGORY_MAP
                .getOrDefault(ext, FileCategory.OTHER);
    }

    /**
     * 统计单个文件的代码行、注释行和空行数量
     *
     * <p>支持的注释格式：</p>
     * <ul>
     *   <li>单行注释：{@code //}</li>
     *   <li>多行注释：{@code /* ... *\/}</li>
     * </ul>
     *
     * <p>说明：</p>
     * <ul>
     *   <li>空行：仅包含空白字符的行</li>
     *   <li>注释行：属于注释内容的行</li>
     *   <li>代码行：非注释、非空行</li>
     * </ul>
     *
     * @param file 需要统计的源码文件
     * @return 行数统计结果 {@link LineStat}
     */
    public static LineStat countLines(File file) {

        LineStat stat = new LineStat();
        boolean inBlockComment = false;

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(file))) {

            String line;
            while ((line = reader.readLine()) != null) {

                stat.setTotalLines(stat.getTotalLines() + 1);
                String trim = line.trim();

                // 空行
                if (trim.isEmpty()) {
                    stat.setBlankLines(stat.getBlankLines() + 1);
                    continue;
                }

                // 处于多行注释中
                if (inBlockComment) {
                    stat.setCommentLines(stat.getCommentLines() + 1);
                    if (trim.contains("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }

                // 单行注释
                if (trim.startsWith("//")) {
                    stat.setCommentLines(stat.getCommentLines() + 1);
                    continue;
                }

                // 多行注释开始
                if (trim.startsWith("/*")) {
                    stat.setCommentLines(stat.getCommentLines() + 1);
                    if (!trim.contains("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }

                // 代码行
                stat.setCodeLines(stat.getCodeLines() + 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return stat;
    }

    public static String readFileContent(String absolutePath) {
        try {
            Path path = Paths.get(absolutePath).normalize();
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            throw new IllegalArgumentException("文件不存在: " + absolutePath);
        } catch (AccessDeniedException e) {
            throw new IllegalArgumentException("没有权限读取文件: " + absolutePath);
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + absolutePath, e);
        }
    }
}