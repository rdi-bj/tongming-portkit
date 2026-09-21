package com.jinw.common.constant;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ScanConstant {

    public static final String LANGUAGE_C = "C";

    public static final String LANGUAGE_CPP = "CPP";

    public static final String LANGUAGE_JAVA = "JAVA";

    public static final Set<String> LANGUAGE_JAVA_SUFFIX = Set.of(
            ".java"
    );

    public static final Set<String> LANGUAGE_C_SUFFIX = Set.of(
            ".c",
            ".h"
    );

    public static final Set<String> LANGUAGE_CPP_SUFFIX = Set.of(
            ".cc",
            ".cpp",
            ".cxx",
            ".hh",
            ".hpp",
            ".hxx"
    );

    public static final String SCAN_FILE_TASK_QUEUE = "scan_file_task_queue";

    public static final String SCAN_FILE_RESULT_QUEUE = "scan_file_result_queue";

    public static final String LLM_FILE_TASK_QUEUE = "llm_file_task_queue";

    public static final String LLM_FILE_RESULT_QUEUE = "llm_file_result_queue";
    /**
     * 通知文件开始扫描
     */
    public static final String SCAN_FILE_START_QUEUE = "scan_file_start";

    public static final String VERIFY_FILE_TASK_QUEUE = "verify_file_task_queue";

    public static final String VERIFY_FILE_RESULT_QUEUE = "verify_file_result_queue";

    public static final String VERIFY_FILE_START_QUEUE = "verify_file_start_queue";

    /**
     * 释放任务队列：通知 worker 中止 opencode session
     */
    public static final String ABORT_TASK_QUEUE = "abort_task_queue";

    public static final String TYPE_INCLUDE = "INCLUDE";

    public static final String TYPE_MACRO = "MACRO";

    public static final String TYPE_ASM = "ASM";

    public static final String TYPE_JAVA = "JAVA";

    public static final String SCAN_STATUS_UNZIP = "UNZIP";

    public static final String SCAN_STATUS_RUNNING = "RUNNING";

    public static final String SCAN_STATUS_FINISHED = "FINISHED";

    public static final String GRAPH_FILE_NAME = "graph.json";

    public static final Map<String, FileCategory> EXTENSION_CATEGORY_MAP =
            new HashMap<String, FileCategory>() {{
                put(".c", FileCategory.C_SOURCE);
                put(".cc", FileCategory.CPP_SOURCE);
                put(".cpp", FileCategory.CPP_SOURCE);
                put(".cxx", FileCategory.CPP_SOURCE);
                put(".h", FileCategory.C_HEADER);
                put(".hh", FileCategory.CPP_HEADER);
                put(".hpp", FileCategory.CPP_HEADER);
                put(".hxx", FileCategory.CPP_HEADER);
                put(".s", FileCategory.ASSEMBLY);
                put(".S", FileCategory.ASSEMBLY);
                put(".asm", FileCategory.ASSEMBLY);
                put(".sh", FileCategory.SCRIPT);
                put(".bash", FileCategory.SCRIPT);
                put(".zsh", FileCategory.SCRIPT);
                put(".py", FileCategory.SCRIPT);
                put(".pl", FileCategory.SCRIPT);
                put(".rb", FileCategory.SCRIPT);
                put(".js", FileCategory.SCRIPT);
                put(".ts", FileCategory.SCRIPT);
                put(".md", FileCategory.DOCUMENT);
                put(".rst", FileCategory.DOCUMENT);
                put(".txt", FileCategory.DOCUMENT);
                put(".adoc", FileCategory.DOCUMENT);
                put(".json", FileCategory.CONFIG);
                put(".yaml", FileCategory.CONFIG);
                put(".yml", FileCategory.CONFIG);
                put(".toml", FileCategory.CONFIG);
                put(".ini", FileCategory.CONFIG);
                put(".cfg", FileCategory.CONFIG);
                put(".conf", FileCategory.CONFIG);
                put(".xml", FileCategory.CONFIG);
                put(".cmake", FileCategory.BUILD);
                put(".mk", FileCategory.BUILD);
                put(".java",FileCategory.OTHER);
            }};
    public static final Map<String, FileCategory> FILE_NAME_CATEGORY_MAP =
            new HashMap<String, FileCategory>() {{
                put("makefile", FileCategory.BUILD);
                put("gnumakefile", FileCategory.BUILD);
                put("cmakelists.txt", FileCategory.BUILD);
                put("configure", FileCategory.BUILD);
                put("configure.ac", FileCategory.BUILD);
                put("configure.in", FileCategory.BUILD);
                put("meson.build", FileCategory.BUILD);
                put("pom.xml",FileCategory.OTHER);
            }};
}
