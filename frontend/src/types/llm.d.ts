// --- LLM / Model Config types ---

export interface BTLlmConfig {
  id: string
  configName: string
  llmUrl: string
  llmModel: string
  llmTemperature: number
  llmTopP: number
  llmMaxTokens: number
  /** Context limit in tokens */
  contextLimit: number
  /** Output limit in tokens */
  outputLimit: number
  /** Thinking budget in tokens */
  thinkingBudgetTokens: number
  /** "0" or "1" */
  llmStream: string
  /** "0" or "1" */
  llmThink: string
  /** "0" or "1" */
  enabled: string
  remark: string
  apiKey: string
  // Audit fields
  createUser: string
  createTime: string
  updateUser: string
  updateTime: string
  isDelete: string
}

export interface LlmConfigPageResult {
  records: BTLlmConfig[]
  total: number
  size: number
  current: number
}

// --- AdaptStatus enum ---
/** Adaptation state: TODO (about to adapt) → READY (queued) → DOING_QUESTION (adapting issues) → DOING_SUGGEST (producing suggestions) → DOING_FILE (adapting files) → FINISH (done) */
export type AdaptStatus =
  | 'TODO'
  | 'READY'
  | 'DOING_QUESTION'
  | 'DOING_SUGGEST'
  | 'DOING_FILE'
  | 'FINISH'

// --- LLM adapt result (issue level) ---
export interface BTQuestionInfo {
  id: string
  text: string
  filePath: string
  startLine: number
  endLine: number
  startCol: number
  endCol: number
  type: string
  questionType: string
  fileType: string
  taskId: string
  ruleId: string | null
  /** Framework the file belongs to — present in the backend entity (BTQuestionInfo#framework) */
  framework?: string
  ruleName?: string
  description?: string
  /** File ID — argument of adaptFile */
  fileId: string
  /** Issue-level adaptation progress */
  status: AdaptStatus
  /** Issue-level adaptation result */
  adaptResult: string
  // Audit fields
  createUser: string
  createTime: string
  updateUser: string
  updateTime: string
  isDelete: string
}

// --- Adaptation scheme results ---
/** Scheme id: A = added file, B = scheme, C = source file modified */
export type SchemeType = 'A' | 'B' | 'C'

/** File-level adaptation scheme (the new adaptResult of /llm/fileAdaptResult in v4) */
export interface AdaptSchemeResult {
  selectedScheme: SchemeType
  reasoning: string
  originalFilePath: string
  suggestedRiscvFilePath: string
  modificationSummary: string
}

// --- LLM adapt result (file level) ---
export interface LlmQuestionResult {
  taskId: string
  filePath: string
  adaptQuestionCount: number
  totalQuestionCount: number
  /** File-level adaptation progress */
  status: AdaptStatus
  /** File-level adaptation scheme (structured) */
  adaptResult: AdaptSchemeResult
  /** Full adapted text of the file (what adaptResult used to hold) */
  adaptText: string
  btQuestionInfos: BTQuestionInfo[]
}

// --- Directory tree node ---
export interface DirectoryNodeDTO {
  name: string
  path: string
  type: 'DIR' | 'FILE'
  level: 'NONE' | 'SEVERE' | 'MODERATE' | 'MILD'
  errCount: number
  childrenCount: number
}
