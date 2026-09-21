package com.jinw.web.util;

import com.jinw.common.constant.ScanConstant;
import com.jinw.web.config.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

@Slf4j
public final class ZipValidatorUtil {

    private static final Set<String> FORBIDDEN_EXTENSIONS =
            Set.of(".o", ".log", ".exe", ".bin", ".so", ".dll");

    /* ================== 规则 ================== */
    private static final Set<String> SOURCE_EXTENSIONS;
    static {
        Set<String> exts = new java.util.HashSet<>();
        exts.addAll(ScanConstant.LANGUAGE_C_SUFFIX);
        exts.addAll(ScanConstant.LANGUAGE_CPP_SUFFIX);
        exts.addAll(ScanConstant.LANGUAGE_JAVA_SUFFIX);
        exts.add(".xml");
        SOURCE_EXTENSIONS = Set.copyOf(exts);
    }
    private static final int MAX_ENTRY_COUNT = 200000;
    private static final long MAX_SINGLE_FILE_SIZE = 20L * 1024 * 1024;
    private static final long MAX_TOTAL_SIZE = 100L * 1024 * 1024;
    private ZipValidatorUtil() {
    }

    /* ================== 校验入口 ================== */

    public static void validate(File file) {
        if (file == null || !file.exists()) {
            throw new BusinessException("文件不能为空");
        }

        String filename = file.getName();
        if (!filename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException("仅支持 ZIP 格式的文件");
        }

        try {
            ZipFile zipFile = openZipFileWithFallback(file.toPath());

            try (zipFile) {
                validateEntries(zipFile);
            }
        } catch (IOException e) {
            log.error("ZIP 文件解析失败，可能不是有效的 ZIP 文件", e);
            throw new BusinessException("不是有效的 ZIP 文件");
        }
    }

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException("仅支持 ZIP 格式的文件");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("zip-validate-", ".zip");
            file.transferTo(tempFile.toFile());
            validate(tempFile.toFile());
        } catch (IOException e) {
            log.error("ZIP 文件解析失败，可能不是有效的 ZIP 文件", e);
            throw new BusinessException("不是有效的 ZIP 文件");
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void validateEntries(ZipFile zipFile) {
        int entryCount = 0;
        long totalSize = 0;
        boolean hasSource = false;

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();

            String name = entry.getName();
            if (name.contains("..") || name.startsWith(File.separator)) {
                throw new BusinessException("非法的 ZIP 文件路径");
            }

            if (entry.isDirectory()) {
                continue;
            }

            if (++entryCount > MAX_ENTRY_COUNT) {
                log.info("ZIP 文件数量过多");
            }

            if (entry.getSize() > MAX_SINGLE_FILE_SIZE) {
                log.info("ZIP 中存在过大的文件");
            }

            totalSize += entry.getSize();
            if (totalSize > MAX_TOTAL_SIZE) {
                log.info("ZIP 总大小超出限制");
            }

            if (entry.getMethod() == ZipEntry.DEFLATED && entry.getCrc() == -1) {
                log.info("不支持加密 ZIP 文件");
            }

            String lowerName = name.toLowerCase();
            for (String ext : FORBIDDEN_EXTENSIONS) {
                if (lowerName.endsWith(ext)) {
                    log.info("禁止上传编译产物或二进制文件");
                }
            }

            for (String ext : SOURCE_EXTENSIONS) {
                if (lowerName.endsWith(ext)) {
                    hasSource = true;
                }
            }
        }

        if (entryCount == 0) {
            throw new BusinessException("ZIP 文件不能为空");
        }

        if (!hasSource) {
            throw new BusinessException("未检测到有效源代码文件");
        }
    }

    private static ZipFile openZipFileWithFallback(Path tempFile) throws IOException {
        try {
            return new ZipFile(tempFile.toFile(), Charset.forName("UTF-8"));
        } catch (IllegalArgumentException | ZipException e) {
            try {
                return new ZipFile(tempFile.toFile(), Charset.forName("GBK"));
            } catch (Exception ex) {
                log.error("使用 GBK 编码打开 ZIP 也失败", ex);
                if (e instanceof IOException) {
                    throw (IOException) e;
                }
                throw new IOException(e);
            }
        }
    }
}