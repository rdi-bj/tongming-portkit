package com.jinw.web.util;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class InstructionSetAnalyzer {
    
    // 指令集特征模式
    private static final Map<String, List<Pattern>> ARCH_PATTERNS = new HashMap<>();
    private static final Map<String, Set<String>> ARCH_KEYWORDS = new HashMap<>();
    private static final Map<String, Set<String>> ARCH_SPECIFIC_FEATURES = new HashMap<>();
    
    static {
        // 初始化x86模式
        List<Pattern> x86Patterns = Arrays.asList(
            // 寄存器
            Pattern.compile("\\b(e?[abcd]x|e?[sd]i|e?[sb]p|e?s[pi]|r[8-9]|r1[0-5])\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(rax|rbx|rcx|rdx|rsi|rdi|rbp|rsp|rip|r[8-9]|r1[0-5])\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b([abcd][hl]|s[icdf]|cs|ds|es|fs|gs|ss)\\b", Pattern.CASE_INSENSITIVE),
            // 指令特征
            Pattern.compile("\\b(movsx|movzx|lea|xchg|cmpxchg|enter|leave|lods|stos|scas|iret)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(fld|fstp|fadd|fmul|fcom|fstcw|fldcw)\\b", Pattern.CASE_INSENSITIVE),
            // 内存操作
            Pattern.compile("\\[.*\\*[1248]\\s*[+-]?\\s*[0-9a-fx]*\\s*\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(qword|dword|word|byte)\\s+ptr\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(offset|near|far|short)\\b", Pattern.CASE_INSENSITIVE),
            // 寻址模式
            Pattern.compile("\\b(mov|add|sub|cmp)\\s+[a-z0-9]+\\s*,\\s*\\[.*\\]", Pattern.CASE_INSENSITIVE)
        );
        
        // 初始化ARM模式
        List<Pattern> armPatterns = Arrays.asList(
            // ARM32寄存器
            Pattern.compile("\\b(r[0-9]|r1[0-2]|sp|lr|pc|cpsr|spsr|fp|ip)\\b", Pattern.CASE_INSENSITIVE),
            // ARM64寄存器
            Pattern.compile("\\b(x[0-9]|x1[0-9]|x2[0-9]|x3[0-1]|w[0-9]|w1[0-9]|w2[0-9]|w3[0-1])\\b", Pattern.CASE_INSENSITIVE),
            // ARM64特殊寄存器
            Pattern.compile("\\b(xzr|wzr|sp|pc)\\b", Pattern.CASE_INSENSITIVE),
            // 指令特征
            Pattern.compile("\\b(ldm|stm)(fd|fa|ed|ea|ia|ib|da|db)?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(push|pop)\\s*\\{.*\\}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(ldr|str)(b|h|sb|sh)?\\s+[rxw]\\d+\\s*,\\s*\\[.*\\]", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(vldr|vstr|vadd|vmul)\\b", Pattern.CASE_INSENSITIVE),
            // 条件执行
            Pattern.compile("\\b(add|sub|mov|ldr|str|cmp|b)(eq|ne|cs|cc|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?\\b", Pattern.CASE_INSENSITIVE),
            // 立即数和移位
            Pattern.compile("#0x[0-9a-f]{1,8}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile(",\\s*#-?\\d+\\b"),
            Pattern.compile(",\\s*(lsl|lsr|asr|ror)\\s+#\\d+\\b", Pattern.CASE_INSENSITIVE),
            // 多寄存器操作
            Pattern.compile("^\\s*\\{.*\\}", Pattern.CASE_INSENSITIVE)
        );
        
        ARCH_PATTERNS.put("x86", x86Patterns);
        ARCH_PATTERNS.put("arm", armPatterns);
        
        // 初始化关键词
        Set<String> x86Keywords = new HashSet<>(Arrays.asList(
            "eax", "ebx", "ecx", "edx", "esi", "edi", "ebp", "esp", "eip",
            "rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rbp", "rsp", "rip",
            "al", "ah", "bl", "bh", "cl", "ch", "dl", "dh",
            "ax", "bx", "cx", "dx", "si", "di", "bp", "sp",
            "cs", "ds", "es", "fs", "gs", "ss",
            "st0", "st1", "st2", "st3", "st4", "st5", "st6", "st7",
            "mm0", "mm1", "mm2", "mm3", "mm4", "mm5", "mm6", "mm7",
            "xmm0", "xmm1", "xmm2", "xmm3", "xmm4", "xmm5", "xmm6", "xmm7",
            "ymm0", "ymm1", "ymm2", "ymm3", "ymm4", "ymm5", "ymm6", "ymm7",
            "zmm0", "zmm1", "zmm2", "zmm3", "zmm4", "zmm5", "zmm6", "zmm7"
        ));
        
        Set<String> armKeywords = new HashSet<>(Arrays.asList(
            // ARM32
            "r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7",
            "r8", "r9", "r10", "r11", "r12", "r13", "r14", "r15",
            "sp", "lr", "pc", "fp", "ip",
            "cpsr", "spsr", "apsr",
            // ARM64
            "x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7",
            "x8", "x9", "x10", "x11", "x12", "x13", "x14", "x15",
            "x16", "x17", "x18", "x19", "x20", "x21", "x22", "x23",
            "x24", "x25", "x26", "x27", "x28", "x29", "x30",
            "w0", "w1", "w2", "w3", "w4", "w5", "w6", "w7",
            "w8", "w9", "w10", "w11", "w12", "w13", "w14", "w15",
            "w16", "w17", "w18", "w19", "w20", "w21", "w22", "w23",
            "w24", "w25", "w26", "w27", "w28", "w29", "w30",
            "xzr", "wzr",
            // 向量寄存器
            "q0", "q1", "q2", "q3", "q4", "q5", "q6", "q7",
            "d0", "d1", "d2", "d3", "d4", "d5", "d6", "d7",
            "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7"
        ));
        
        ARCH_KEYWORDS.put("x86", x86Keywords);
        ARCH_KEYWORDS.put("arm", armKeywords);
        
        // 初始化特定特征
        ARCH_SPECIFIC_FEATURES.put("x86", new HashSet<>(Arrays.asList(
            "ptr", "offset", "segment", "far", "near", "short",
            "db", "dw", "dd", "dq", "dt", "resb", "resw", "resd", "resq",
            "times", "equ", "dup", "org", "section", "global", "extern"
        )));
        
        ARCH_SPECIFIC_FEATURES.put("arm", new HashSet<>(Arrays.asList(
            ".thumb", ".arm", ".code", ".text", ".data",
            ".word", ".hword", ".byte", ".align", ".pool",
            "adr", "adrp", "tst", "teq", "cmn", "bic", "orn", "eor", "orr", "mvn",
            "mul", "mla", "umull", "smull", "umlal", "smlal",
            "svc", "hvc", "smc", "brk", "hlt", "dcps", "clrex", "sev", "wfe", "wfi", "yield"
        )));
    }
    
    /**
     * 分析指令集代码列表
     * @param instructions 指令列表
     * @return 分析结果对象
     */
    public static AnalysisResult analyzeInstructionSet(List<String> instructions) {
        if (instructions == null || instructions.isEmpty()) {
            return new AnalysisResult("unknown", 0.0, new HashMap<>());
        }
        
        Map<String, Double> scores = new HashMap<>();
        scores.put("x86", 0.0);
        scores.put("arm", 0.0);
        
        Map<String, List<String>> matchedPatterns = new HashMap<>();
        matchedPatterns.put("x86", new ArrayList<>());
        matchedPatterns.put("arm", new ArrayList<>());
        
        Map<String, List<String>> matchedKeywords = new HashMap<>();
        matchedKeywords.put("x86", new ArrayList<>());
        matchedKeywords.put("arm", new ArrayList<>());
        
        int totalLines = 0;
        
        for (String line : instructions) {
            if (line == null || line.trim().isEmpty() || line.trim().startsWith(";") || 
                line.trim().startsWith("#") || line.trim().startsWith("//")) {
                continue; // 跳过空行和注释
            }
            
            totalLines++;
            String lineTrimmed = line.trim();
            
            // 1. 关键词匹配（高权重）
            for (String arch : Arrays.asList("x86", "arm")) {
                for (String keyword : ARCH_KEYWORDS.get(arch)) {
                    String pattern = "\\b" + Pattern.quote(keyword.toLowerCase()) + "\\b";
                    if (lineTrimmed.toLowerCase().matches(".*" + pattern + ".*")) {
                        scores.put(arch, scores.get(arch) + 3.0);
                        matchedKeywords.get(arch).add(keyword + " in: " + line);
                    }
                }
            }
            
            // 2. 模式匹配（中权重）
            for (String arch : Arrays.asList("x86", "arm")) {
                for (Pattern pattern : ARCH_PATTERNS.get(arch)) {
                    Matcher matcher = pattern.matcher(lineTrimmed);
                    if (matcher.find()) {
                        scores.put(arch, scores.get(arch) + 2.0);
                        matchedPatterns.get(arch).add(pattern.pattern() + " in: " + line);
                    }
                }
            }
            
            // 3. 特定特征匹配（最高权重）
            for (String arch : Arrays.asList("x86", "arm")) {
                for (String feature : ARCH_SPECIFIC_FEATURES.get(arch)) {
                    if (lineTrimmed.toLowerCase().contains(feature.toLowerCase())) {
                        scores.put(arch, scores.get(arch) + 5.0);
                        matchedPatterns.get(arch).add("Feature: " + feature + " in: " + line);
                    }
                }
            }
            
            // 4. 指令结构特征
            analyzeStructuralFeatures(lineTrimmed, scores);
        }
        
        // 计算概率
        double x86Score = scores.get("x86");
        double armScore = scores.get("arm");
        double totalScore = x86Score + armScore;
        
        String predictedArch = "unknown";
        double confidence = 0.0;
        
        if (totalScore > 0) {
            if (x86Score > armScore) {
                predictedArch = "x86";
                confidence = x86Score / totalScore;
            } else if (armScore > x86Score) {
                predictedArch = "arm";
                confidence = armScore / totalScore;
            } else {
                predictedArch = "ambiguous";
                confidence = 0.5;
            }
        }
        
        // 考虑代码行数对置信度的影响
        double lineFactor = Math.min(1.0, totalLines / 10.0); // 最多10行达到最大影响
        confidence = confidence * 0.7 + (lineFactor * 0.3);
        
        return new AnalysisResult(predictedArch, confidence, scores, 
                                 matchedPatterns, matchedKeywords, totalLines);
    }
    
    /**
     * 分析结构特征
     */
    private static void analyzeStructuralFeatures(String line, Map<String, Double> scores) {
        String lineLower = line.toLowerCase();
        
        // 检查指令长度模式
        String[] tokens = line.split("\\s+");
        if (tokens.length > 0) {
            String firstToken = tokens[0].toLowerCase();
            
            // x86特征：指令通常较短，有内存操作数
            if (line.contains("[") && line.contains("]")) {
                // 检查是否是x86风格的内存操作
                if (line.matches(".*\\[[^\\]]*\\+[^\\]]*\\].*") || 
                    line.matches(".*\\[[^\\]]*\\*[^\\]]*\\].*")) {
                    scores.put("x86", scores.get("x86") + 1.5);
                }
                // 检查是否是ARM风格的偏移寻址
                else if (line.matches(".*\\[[^\\]]*\\s*,\\s*#.*\\].*")) {
                    scores.put("arm", scores.get("arm") + 1.5);
                }
            }
            
            // ARM特征：条件执行
            if (firstToken.matches(".*(eq|ne|cs|cc|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)$")) {
                scores.put("arm", scores.get("arm") + 2.0);
            }
            
            // x86特征：段寄存器前缀
            if (line.matches("^\\s*[a-z]{2}:.*")) {
                scores.put("x86", scores.get("x86") + 2.0);
            }
            
            // ARM特征：多寄存器操作
            if (line.contains("{") && line.contains("}")) {
                scores.put("arm", scores.get("arm") + 1.5);
            }
        }
    }
    
    /**
     * 批量分析并统计
     */
    public static BatchAnalysisResult batchAnalyze(List<List<String>> instructionLists) {
        int x86Count = 0;
        int armCount = 0;
        int unknownCount = 0;
        
        List<AnalysisResult> results = new ArrayList<>();
        Map<String, Integer> archDistribution = new HashMap<>();
        archDistribution.put("x86", 0);
        archDistribution.put("arm", 0);
        archDistribution.put("unknown", 0);
        archDistribution.put("ambiguous", 0);
        
        for (List<String> instructions : instructionLists) {
            AnalysisResult result = analyzeInstructionSet(instructions);
            results.add(result);
            
            String arch = result.getArchitecture();
            archDistribution.put(arch, archDistribution.getOrDefault(arch, 0) + 1);
            
            if (arch.equals("x86")) {
                x86Count++;
            } else if (arch.equals("arm")) {
                armCount++;
            } else if (arch.equals("unknown") || arch.equals("ambiguous")) {
                unknownCount++;
            }
        }
        
        int total = instructionLists.size();
        double x86Percent = total > 0 ? (double) x86Count / total * 100 : 0;
        double armPercent = total > 0 ? (double) armCount / total * 100 : 0;
        double unknownPercent = total > 0 ? (double) unknownCount / total * 100 : 0;
        
        return new BatchAnalysisResult(results, archDistribution, 
                                      x86Percent, armPercent, unknownPercent);
    }
    
    /**
     * 分析结果类
     */
    public static class AnalysisResult {
        private final String architecture;
        private final double confidence;
        private final Map<String, Double> scores;
        private final Map<String, List<String>> matchedPatterns;
        private final Map<String, List<String>> matchedKeywords;
        private final int lineCount;
        
        public AnalysisResult(String architecture, double confidence, 
                            Map<String, Double> scores,
                            Map<String, List<String>> matchedPatterns,
                            Map<String, List<String>> matchedKeywords,
                            int lineCount) {
            this.architecture = architecture;
            this.confidence = Math.min(1.0, Math.max(0.0, confidence));
            this.scores = new HashMap<>(scores);
            this.matchedPatterns = new HashMap<>(matchedPatterns);
            this.matchedKeywords = new HashMap<>(matchedKeywords);
            this.lineCount = lineCount;
        }
        
        public AnalysisResult(String architecture, double confidence, Map<String, Double> scores) {
            this(architecture, confidence, scores, 
                 new HashMap<>(), new HashMap<>(), 0);
        }
        
        public String getArchitecture() { return architecture; }
        public double getConfidence() { return confidence; }
        public double getX86Score() { return scores.getOrDefault("x86", 0.0); }
        public double getArmScore() { return scores.getOrDefault("arm", 0.0); }
        public int getLineCount() { return lineCount; }
        public Map<String, List<String>> getMatchedPatterns() { return matchedPatterns; }
        public Map<String, List<String>> getMatchedKeywords() { return matchedKeywords; }
        
        public String getFormattedResult() {
            return String.format("架构: %s (%.1f%% 置信度)\n" +
                               "x86得分: %.2f\n" +
                               "ARM得分: %.2f\n" +
                               "分析行数: %d",
                               architecture.toUpperCase(), confidence * 100,
                               getX86Score(), getArmScore(), lineCount);
        }
        
        public void printDetailedAnalysis() {
            System.out.println("===== 详细分析结果 =====");
            System.out.println(getFormattedResult());
            System.out.println("\n匹配的x86关键词:");
            matchedKeywords.get("x86").forEach(System.out::println);
            System.out.println("\n匹配的ARM关键词:");
            matchedKeywords.get("arm").forEach(System.out::println);
            System.out.println("\n匹配的x86模式:");
            matchedPatterns.get("x86").forEach(System.out::println);
            System.out.println("\n匹配的ARM模式:");
            matchedPatterns.get("arm").forEach(System.out::println);
            System.out.println("=======================");
        }
    }
    
    /**
     * 批量分析结果类
     */
    public static class BatchAnalysisResult {
        private final List<AnalysisResult> individualResults;
        private final Map<String, Integer> distribution;
        private final double x86Percentage;
        private final double armPercentage;
        private final double unknownPercentage;
        
        public BatchAnalysisResult(List<AnalysisResult> individualResults,
                                  Map<String, Integer> distribution,
                                  double x86Percentage, double armPercentage, 
                                  double unknownPercentage) {
            this.individualResults = individualResults;
            this.distribution = distribution;
            this.x86Percentage = x86Percentage;
            this.armPercentage = armPercentage;
            this.unknownPercentage = unknownPercentage;
        }
        
        public void printStatistics() {
            System.out.println("===== 批量分析统计 =====");
            System.out.printf("x86: %.1f%% (%d个样本)\n", x86Percentage, distribution.get("x86"));
            System.out.printf("ARM: %.1f%% (%d个样本)\n", armPercentage, distribution.get("arm"));
            System.out.printf("未知/模糊: %.1f%% (%d个样本)\n", 
                            unknownPercentage, 
                            distribution.get("unknown") + distribution.get("ambiguous"));
            System.out.println("=======================");
        }
    }

    public static List<String> parseAsmTextToList(String asmText) {
        if (asmText == null || asmText.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();

        // 先按换行拆分
        String[] lines = asmText.split("\\r?\\n");

        StringBuilder multiLineBuffer = new StringBuilder();

        for (String line : lines) {
            if (line == null) {
                continue;
            }

            String trimmed = line.trim();

            // 跳过空行
            if (trimmed.isEmpty()) {
                continue;
            }

            // 处理 C 字符串拼接（\"...\n\t\" 这种）
            // 去掉开头的引号和结尾的引号/逗号
            if (trimmed.startsWith("\"") && trimmed.contains("\\n")) {
                // 去掉首尾的引号
                trimmed = trimmed.substring(1);
                if (trimmed.endsWith("\"")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
                // 去掉结尾的逗号（如果有）
                if (trimmed.endsWith(",")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                }
            }

            // 处理续行（反斜杠结尾）
            if (trimmed.endsWith("\\")) {
                multiLineBuffer.append(trimmed, 0, trimmed.length() - 1).append(" ");
                continue;
            }

            // 如果有缓冲的续行内容，合并进来
            if (multiLineBuffer.length() > 0) {
                trimmed = multiLineBuffer.append(trimmed).toString();
                multiLineBuffer.setLength(0);
            }

            // 清理转义字符 \n \t
            trimmed = trimmed.replaceAll("\\\\n", "\n")
                    .replaceAll("\\\\t", " ")
                    .replaceAll("\\s+", " ");

            // 按 \n 再次拆分（因为上面可能引入了换行）
            String[] instructions = trimmed.split("\\n");
            for (String instr : instructions) {
                String cleanInstr = instr.trim();
                if (!cleanInstr.isEmpty()) {
                    result.add(cleanInstr);
                }
            }
        }

        return result;
    }
    
    /**
     * 使用示例
     */
    public static void main(String[] args) {
        // 测试数据
        List<List<String>> testSamples = new ArrayList<>();
        
        // 1. x86代码
        testSamples.add(Arrays.asList(
            "section .text",
            "global _start",
            "_start:",
            "    mov eax, 1        ; 系统调用号 (sys_exit)",
            "    mov ebx, 42       ; 退出状态",
            "    int 0x80         ; 调用内核"
        ));
        
        // 2. x86-64代码
        testSamples.add(Arrays.asList(
            "push rbp",
            "mov rbp, rsp",
            "sub rsp, 32",
            "mov dword [rbp-4], 10",
            "mov eax, dword [rbp-4]",
            "add eax, 5",
            "mov dword [rbp-8], eax",
            "mov eax, 0",
            "leave",
            "ret"
        ));
        
        // 3. ARM32代码
        testSamples.add(Arrays.asList(
            ".global main",
            ".text",
            "main:",
            "    push {fp, lr}",
            "    add fp, sp, #4",
            "    sub sp, sp, #16",
            "    mov r0, #10",
            "    str r0, [fp, #-8]",
            "    ldr r0, [fp, #-8]",
            "    add r0, r0, #5",
            "    str r0, [fp, #-12]",
            "    mov r0, #0",
            "    sub sp, fp, #4",
            "    pop {fp, pc}"
        ));
        
        // 4. ARM64代码
        testSamples.add(Arrays.asList(
            ".global _start",
            ".text",
            "_start:",
            "    stp x29, x30, [sp, -16]!",
            "    mov x29, sp",
            "    sub sp, sp, 32",
            "    mov w0, 10",
            "    str w0, [x29, -4]",
            "    ldr w0, [x29, -4]",
            "    add w0, w0, 5",
            "    str w0, [x29, -8]",
            "    mov w0, 0",
            "    ldp x29, x30, [sp], 16",
            "    ret"
        ));
        
        // 5. 混合代码（测试模糊情况）
        testSamples.add(Arrays.asList(
            "mov eax, 10",
            "ldr r0, =0x1000",
            "add r0, r0, #4",
            "int 0x80"
        ));
        
        // 分析每个样本
        for (int i = 0; i < testSamples.size(); i++) {
            System.out.println("\n=== 样本 " + (i+1) + " 分析 ===");
            AnalysisResult result = analyzeInstructionSet(testSamples.get(i));
            System.out.println(result.getArchitecture());
            
//            if (result.getConfidence() < 0.7) {
//                System.out.println("⚠️  置信度较低，建议手动检查");
//            }
//
//            // 显示前3个匹配的关键词
//            System.out.println("检测到的关键词:");
//            List<String> x86Keywords = result.getMatchedKeywords().get("x86");
//            List<String> armKeywords = result.getMatchedKeywords().get("arm");
//            if (!x86Keywords.isEmpty()) {
//                System.out.println("  x86: " + x86Keywords.subList(0, Math.min(3, x86Keywords.size())));
//            }
//            if (!armKeywords.isEmpty()) {
//                System.out.println("  ARM: " + armKeywords.subList(0, Math.min(3, armKeywords.size())));
//            }
        }
        
        // 批量分析统计
//        System.out.println("\n=== 批量分析统计 ===");
//        BatchAnalysisResult batchResult = batchAnalyze(testSamples);
//        batchResult.printStatistics();
//
//        // 单个复杂示例
//        System.out.println("\n=== 复杂示例详细分析 ===");
//        List<String> complexCode = Arrays.asList(
//            "global _start",
//            "section .text",
//            "_start:",
//            "    mov rax, 1          ; write syscall",
//            "    mov rdi, 1          ; stdout",
//            "    lea rsi, [msg]      ; buffer",
//            "    mov rdx, len        ; length",
//            "    syscall",
//            "",
//            "    mov rax, 60         ; exit syscall",
//            "    xor rdi, rdi        ; exit code 0",
//            "    syscall",
//            "",
//            "section .data",
//            "msg db 'Hello World!', 0xA",
//            "len equ $ - msg"
//        );
//
//        AnalysisResult detailedResult = analyzeInstructionSet(complexCode);
//        detailedResult.printDetailedAnalysis();
    }
}