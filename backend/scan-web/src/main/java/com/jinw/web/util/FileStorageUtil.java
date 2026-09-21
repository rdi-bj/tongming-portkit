package com.jinw.web.util;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class FileStorageUtil {

    /**
     * 根路径（由 Spring 注入）
     */
    private static String baseFilePath;

    @Value("${cscan.file-path:./temp/}")
    private String filePath;

    /**
     * 保存文件
     *
     * @param file  MultipartFile
     * @param paths 可变参数：文件夹 / 子路径 / 文件名
     * @return 相对路径（用于数据库存储）
     */
    public static File save(MultipartFile file, String... paths) {
        try {
            if (baseFilePath == null) {
                throw new IllegalStateException("filePath 未初始化");
            }

            Path base = Paths.get(baseFilePath)
                    .normalize()
                    .toAbsolutePath();

            // 拼接路径
            Path target = base;
            for (String p : paths) {
                target = target.resolve(p);
            }

            // 防止路径穿越
            target = target.normalize();
            if (!target.startsWith(base)) {
                throw new IllegalArgumentException("非法文件路径");
            }

            // 自动创建父目录
            Files.createDirectories(target.getParent());

            // 保存文件
            file.transferTo(target);

            return target.toFile();

        } catch (Exception e) {
            throw new RuntimeException("文件保存失败", e);
        }
    }

    /**
     * 仅生成 Path（不保存文件）,生成baseFilePath下的路径
     *
     * @param paths 后续需要拼接的路径，如果是绝对路径会自动转化为相对路径
     * @return 生成baseFilePath下的路径
     */
    public static Path resolve(String... paths) {
        Path base = Paths.get(baseFilePath)
                .normalize()
                .toAbsolutePath();

        return resolveByBase(base, paths);
    }

    /**
     * 生成指定base下的路径
     *
     * @param base
     * @param paths
     * @return
     */
    public static Path resolveByBase(Path base, String... paths) {
        Path result = base;

        for (String p : paths) {
            if (p == null || p.isBlank()) {
                continue;
            }

            // 关键：强制转为相对路径
            String safe = p.replaceAll("^[\\\\/]+", "");

            // Windows 盘符防护（如 C:）
            if (safe.matches("^[a-zA-Z]:.*")) {
                throw new IllegalArgumentException("不允许使用盘符路径: " + p);
            }

            Path segment = Paths.get(safe);
            result = result.resolve(segment).normalize();
        }

        // 最终边界检查（兜底）
        if (!result.normalize().startsWith(base)) {
            throw new SecurityException("路径越权: " + result);
        }

        return result;
    }

    /**
     * 将路径转为相对于 baseFilePath 的相对路径
     *
     * @param path 绝对路径 或 相对路径
     * @return 相对路径（统一使用 /）
     */
    public static String toRelativePath(String path) {
        return toRelativePath(File.separator, path);
    }

    /**
     * 将路径转为相对于 baseFilePath 的相对路径
     *
     * @param pre  除了baseFilePath，后续还要剔除掉的相对路径，比如 taskID/unzip
     * @param path 绝对路径 或 相对路径
     * @return 相对路径（统一使用 /）
     */
    public static String toRelativePath(String pre, String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path 不能为空");
        }

        Path inputPath = Paths.get(path).normalize();

        // 如果是相对路径，直接返回
        if (!inputPath.isAbsolute()) {
            return path.replace(File.separatorChar, '/');
        }

        // 构建需要去除的路径
        Path base = Paths.get(baseFilePath + File.separator + pre)
                .normalize()
                .toAbsolutePath();

        if (!inputPath.startsWith(base)) {
            throw new IllegalArgumentException("路径不在 baseFilePath 范围内");
        }

        Path relativePath = base.relativize(inputPath);
        return relativePath.toString().replace(File.separatorChar, '/');
    }

    /**
     * 将相对路径转换成绝对路径
     *
     * @param pre          除了baseFilePath的前缀
     * @param relativePath 相对路径
     * @return 绝对路径（统一使用 /）
     */
    public static String toAbsolutePath(String pre, String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("relativePath 不能为空");
        }

        // 先规范化 base 路径
        Path base = Paths.get(baseFilePath, pre)
                .normalize()
                .toAbsolutePath();

        // 解析相对路径（不使用 Paths.get，防止 ../ 逃逸）
        Path resolved = base.resolve(relativePath).normalize();

        // 安全校验：不能跳出 base
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("相对路径非法，试图跳出 baseFilePath 目录");
        }

        return resolved.toString().replace(File.separatorChar, '/');
    }

    @PostConstruct
    public void init() {
        baseFilePath = filePath;
    }

}