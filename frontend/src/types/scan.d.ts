import type { AdaptStatus } from './llm'

// --- File info types ---
export interface FileInfoCategoryRaw {
  fileCount: number
  codeLines: number
  commentLines: number
  blankLines: number
  totalLines: number
}

export interface FileInfoCategory {
  fileCount: number
  lineCount: number
}

// --- Report types (getReportInfo / downloadReport) ---
export interface ReportIssue {
  index: number
  description: string
  suggestion: string
  fileName: string
  lineNumber: number
}

export interface ReportInfo {
  subjectName: string
  reportDate: string
  platformName: string
  version: string
  language: string
  /** OS name (shown in the report basics) */
  systemName?: string
  /** OS version (shown in the report basics) */
  systemVersion?: string
  /** Build system (returned by the backend, e.g. "MAKE") */
  buildSystem?: string
  totalLines: string
  totalFiles: string
  sourceCodeLines?: string
  originalArch: string
  /** Source file / line counts (architecture-related file detection) */
  srcFileCount: number
  srcFileLines: number
  asmFileCount: number
  asmFileLines: number
  buildFileCount: number
  buildFileLines: number
  /** Other file / line counts */
  otherFileCount: number
  otherFileLines: number
  /** File path (legacy endpoints may return it; new ones use line fields such as srcFileLines) */
  srcFilePath?: string
  asmFilePath?: string
  buildFilePath?: string
  scannedFiles: number
  codeLines: number
  modifyFiles: number
  modifyLocations: number
  coveredRules: number
  headerIssues: number
  headerPercent: string
  macroIssues: number
  macroPercent: string
  asmIssues: number
  asmPercent: string
  /** Distribution of code to modify — other */
  otherIssues: number
  otherPercent: string
  coreSrcFiles: number
  coreSrcLines: number
  coreSrcPercent: string
  coreAsmFiles: number
  coreAsmLines: number
  coreAsmPercent: string
  coreBuildFiles: number
  coreBuildLines: number
  coreBuildPercent: string
  conclusionArchIssues: number
  conclusionAsmIssues: number
  conclusionBuildIssues: number
  conclusionOtherIssues: number
  /** Attachment issue details (the backend returns a flat array) */
  issues?: ReportIssue[]
}

// --- App/Project types ---
export interface BTAppInfo {
  id: string
  name: string
  description: string
  fileName: string | null
  filePath: string | null
  taskId: string | null
  fileInfo: string | null
  fileTree: string | null
  isDelete: string
  status: string
  /** OS name (required when creating a project) */
  systemName?: string
  /** OS version (optional when creating a project) */
  systemVersion?: string
  /** Batch adaptation state: "0" not adapted | "1" adapting | "-1" adapted */
  adaptStatus?: string
  /** Adapted files */
  adaptQuestionFile?: number | string
  /** Total files */
  totalQuestionFile?: number | string
  /** Adapted issues */
  adaptQuestion?: number | string
  /** Total issues */
  totalQuestion?: number | string
  createTime: string | null
  updateTime: string | null
  scanTime: string | null
}

export interface AppInfoPageResult {
  records: BTAppInfo[]
  total: number
  size: number
  current: number
}

// --- Scan progress ---
export interface ScanProgress {
  total: number
  completed: number
  status: string
  startTime: number
  /** Unzip progress percentage 0-100 (pushed by the backend) */
  unzipPercent?: number
  /** Total files to verify (stage 03, smart review) */
  totalVerifyFileCount?: number
  /** Files already verified (stage 03, smart review) */
  alreadyVerifyFileCount?: number
}

// --- Framework Portrait types ---
export interface FrameworkPortraitData {
  questionType: {
    INCLUDE: number
    MACRO: number
    ASM: number
  }
  fileTypeStats: {
    C_SOURCE: number
    CPP_SOURCE: number
    C_HEADER: number
    CPP_HEADER: number
    ASSEMBLY: number
    SCRIPT: number
    DOCUMENT: number
    CONFIG: number
    BUILD: number
    OTHER: number
  }
}

export interface LibListEntry {
  libName: string
  libCategory: string
  supportRiscv: string
}

export interface LibListRaw {
  LIB_NAME: string
  LIB_CATEGORY: string
  SUPPORT_RISCV: string
}

export interface QuestionInfo {
  id: string
  text: string
  filePath: string
  startLine: number
  endLine: number
  startCol: number
  endCol: number
  type: string
  taskId: string
  ruleId: string | null
  ruleName?: string
  description?: string
  /** Issue type (new in v3) */
  questionType?: string
  /** File type (new in v3) */
  fileType?: string
  /** File ID — argument of adaptFile (new in v3) */
  fileId?: string
  /** Issue-level adaptation progress (new in v3) */
  status?: AdaptStatus
  /** Issue-level adaptation result (new in v3) */
  adaptResult?: string
}

export interface AsmFileInfo {
  asmInfo: {
    total: number
    asmFile: number
    inlineAsm: number
  }
  instructionSetCodeCount: Record<string, number>
}

// --- Graph / CallEdge types ---
export interface FunctionKey {
  definedInFile: string | null
  functionName: string
}

export interface CallEdge {
  caller: FunctionKey
  callee: FunctionKey
  file: string
  line: number
  indirect: boolean
  unresolved: boolean
}
