package com.jinw.common.utils;

import com.jinw.common.domain.BuildSystemInfo;
import com.jinw.common.enums.BuildSystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BuildSystemDetector {

    private BuildSystemDetector() {}

    public static BuildSystemInfo detect(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return unknown(file);
        }

        String name = file.getFileName().toString();
        String content = readFirstKb(file);

        // ===== CMake =====
        if (name.equalsIgnoreCase("CMakeLists.txt")) {
            int score = 50;
            if (containsAny(content,
                    "cmake_minimum_required", "project(", "add_executable")) {
                score += 40;
            }
            return new BuildSystemInfo(BuildSystem.CMAKE, score, file);
        }

        // ===== Make =====
        if (name.equals("Makefile") || name.equals("makefile") || name.equals("GNUmakefile") || name.endsWith(".mk")) {
            int score = 50;
            if (containsAny(content,
                    "CC=", "CFLAGS=", "LDFLAGS=",
                    "SRCS=", "OBJS=", "TARGET=",
                    "$(CC)", "gcc", "clang",
                    ".c.o:", "%.o: %.c")) {
                score += 40;
            }
            if (content.contains(":=") || content.contains(" = ")) {
                score += 10;
            }
            if (containsAny(content, "all:", "clean:", "gcc", "$(CC)")) {
                score += 30;
            }
            if (content.contains("\t")) {
                score += 20;
            }
            return new BuildSystemInfo(BuildSystem.MAKE, Math.min(score, 100), file);
        }

        // ===== Autotools =====
        if (name.endsWith(".ac") || name.endsWith(".am") || name.endsWith(".in")) {
            int score = 40;
            if (containsAny(content, "AC_INIT", "AM_INIT_AUTOMAKE", "AC_CONFIG_FILES")) {
                score += 50;
            }
            return new BuildSystemInfo(BuildSystem.AUTOTOOLS, Math.min(score, 100), file);
        }

        // ===== Meson =====
        if (name.equalsIgnoreCase("meson.build")) {
            int score = 60;
            if (containsAny(content, "project(", "executable(")) {
                score += 30;
            }
            return new BuildSystemInfo(BuildSystem.MESON, Math.min(score, 100), file);
        }

        // ===== Bazel =====
        if (name.equals("BUILD") || name.equals("BUILD.bazel")) {
            int score = 50;
            if (containsAny(content, "cc_binary", "cc_library")) {
                score += 40;
            }
            return new BuildSystemInfo(BuildSystem.BAZEL, Math.min(score, 100), file);
        }

        // ===== Ninja =====
        if (name.equalsIgnoreCase("build.ninja")) {
            int score = 70;
            if (containsAny(content, "rule ", "build ")) {
                score += 20;
            }
            return new BuildSystemInfo(BuildSystem.NINJA, Math.min(score, 100), file);
        }

        // ===== QMake =====
        if (name.endsWith(".pro")) {
            return new BuildSystemInfo(BuildSystem.QMAKE, 90, file);
        }

        return unknown(file);
    }

    /* ================== 工具方法 ================== */

    private static BuildSystemInfo unknown(Path file) {
        return new BuildSystemInfo(BuildSystem.UNKNOWN, 0, file);
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private static String readFirstKb(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int len = Math.min(bytes.length, 8 * 1024);
        return new String(bytes, 0, len);
    }
}