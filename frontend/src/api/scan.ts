import type { BTQuestionInfo } from '@/types/llm'
import type {
  AppInfoPageResult,
  AsmFileInfo,
  BTAppInfo,
  CallEdge,
  FileInfoCategory,
  FileInfoCategoryRaw,
  FrameworkPortraitData,
  LibListEntry,
  LibListRaw,
  QuestionInfo,
  ReportInfo,
} from '@/types/scan'
import scanRequest, { downloadBlob } from '@/utils/scan-request'

export function parseFileInfo(raw: string | null): Record<string, FileInfoCategory> | null {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Record<string, FileInfoCategoryRaw>
    const result: Record<string, FileInfoCategory> = {}
    for (const [key, val] of Object.entries(parsed)) {
      result[key] = {
        fileCount: val.fileCount,
        lineCount: val.codeLines,
      }
    }
    return result
  } catch {
    return null
  }
}

export async function createAppInfo(
  name: string,
  description?: string,
  systemName?: string,
  systemVersion?: string,
) {
  return scanRequest('/cscan/create', {
    method: 'POST',
    data: { name, description: description || undefined, systemName, systemVersion },
  })
}

export async function updateAppInfo(
  id: string,
  name: string,
  description?: string,
  systemName?: string,
  systemVersion?: string,
) {
  return scanRequest('/cscan/update', {
    method: 'PUT',
    data: { id, name, description: description || undefined, systemName, systemVersion },
  })
}

export async function deleteAppInfo(id: string) {
  return scanRequest(`/cscan/${id}`, {
    method: 'DELETE',
  })
}

export async function getAppInfoDetail(id: string) {
  return scanRequest<BTAppInfo>(`/cscan/${id}`, {
    method: 'GET',
  })
}

export async function getAppInfoPage(pageNum: number, pageSize: number, keyword?: string) {
  const params: Record<string, unknown> = { pageNum, pageSize }
  if (keyword) {
    params.keyword = keyword
  }
  return scanRequest<AppInfoPageResult>('/cscan/page', {
    method: 'GET',
    params,
  })
}

/** Get the projects queued for and undergoing scanning (detection queue) */
export async function getScanQueueList() {
  return scanRequest<BTAppInfo[]>('/cscan/scanQueueList', {
    method: 'GET',
  })
}

export async function startScan(file: File, appId: string, scanType: string = '0') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('appId', appId)
  formData.append('scanType', scanType)

  return scanRequest<string>('/cscan/startScan', {
    method: 'POST',
    data: formData,
  })
}

export async function getFrameworkPortrait(taskId: string) {
  return scanRequest<FrameworkPortraitData>('/cscan/frameworkPortrait', {
    method: 'GET',
    params: { taskId },
  })
}

export async function getMakeFileInfo(taskId: string) {
  return scanRequest<BTQuestionInfo[]>('/cscan/getMakeFileInfo', {
    method: 'GET',
    params: { taskId },
  })
}

export async function getLibList(taskId: string): Promise<LibListEntry[]> {
  return scanRequest<LibListRaw[]>('/cscan/libList', {
    method: 'GET',
    params: { taskId },
  }).then((data) => {
    return data.map((item) => ({
      libName: item.LIB_NAME,
      libCategory: item.LIB_CATEGORY,
      supportRiscv: item.SUPPORT_RISCV,
    }))
  })
}

export async function getQuestionList(taskId: string, isAdapt: string) {
  return scanRequest<QuestionInfo[]>('/cscan/questionList', {
    method: 'GET',
    params: { taskId, isAdapt },
  })
}

export async function getAsmList(taskId: string) {
  return scanRequest<AsmFileInfo>('/cscan/asmList', {
    method: 'GET',
    params: { taskId },
  })
}

export async function getFileText(filePath: string, taskId: string) {
  return scanRequest<string>('/cscan/getFileText', {
    method: 'GET',
    params: { filePath, taskId },
  })
}

export async function getFileQuestions(filePath: string, taskId: string, isAdapt: string) {
  return scanRequest<QuestionInfo[]>('/cscan/getFileQuestions', {
    method: 'GET',
    params: { filePath, taskId, isAdapt },
  })
}

export async function getProjectGraph(taskId: string) {
  return scanRequest<CallEdge[]>('/cscan/getProjectGraph', {
    method: 'GET',
    params: { taskId },
  })
}

export async function getReportInfo(id: string) {
  return scanRequest<ReportInfo>('/cscan/getReportInfo', {
    method: 'GET',
    params: { id },
  })
}

export async function downloadReport(id: string, status: string) {
  return downloadBlob('/cscan/downloadReport', { id, status })
}
