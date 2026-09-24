import type {
  BTLlmConfig,
  DirectoryNodeDTO,
  LlmConfigPageResult,
  LlmQuestionResult,
} from '@/types/llm'
import type { AppInfoPageResult } from '@/types/scan'
import scanRequest from '@/utils/scan-request'

export async function getLlmConfigPage(pageNum: number, pageSize: number, keyword?: string) {
  const params: Record<string, unknown> = { pageNum, pageSize }
  if (keyword) {
    params.keyword = keyword
  }
  return scanRequest<LlmConfigPageResult>('/llm/page', {
    method: 'GET',
    params,
  })
}

export async function getLlmConfigDetail(id: string) {
  return scanRequest<BTLlmConfig>(`/llm/${id}`, {
    method: 'GET',
  })
}

export async function createLlmConfig(data: Partial<BTLlmConfig>) {
  return scanRequest<string>('/llm/create', {
    method: 'POST',
    data,
  })
}

export async function updateLlmConfig(data: Partial<BTLlmConfig>) {
  return scanRequest<string>('/llm/update', {
    method: 'PUT',
    data,
  })
}

export async function deleteLlmConfig(id: string) {
  return scanRequest<string>(`/llm/${id}`, {
    method: 'DELETE',
  })
}

export async function changeLlmConfig(id: string) {
  return scanRequest<string>('/llm/changeLlmConfig', {
    method: 'POST',
    params: { id },
  })
}

export async function adaptFile(fileId: string) {
  return scanRequest<string>('/llm/adaptFile', {
    method: 'POST',
    params: { fileId },
  })
}

export async function adaptProject(taskId: string) {
  return scanRequest<string>('/llm/adaptProject', {
    method: 'POST',
    params: { taskId },
  })
}

export async function getFileAdaptResult(fileId: string) {
  return scanRequest<LlmQuestionResult>('/llm/fileAdaptResult', {
    method: 'GET',
    params: { fileId },
  })
}

export async function getAdaptableProjects(pageNum: number, pageSize: number, keyword?: string) {
  const params: Record<string, unknown> = { pageNum, pageSize }
  if (keyword) {
    params.keyword = keyword
  }
  return scanRequest<AppInfoPageResult>('/llm/pageList', {
    method: 'GET',
    params,
  })
}

export async function getProjectTree(taskId: string, parentPath?: string, isAdapt?: string) {
  const params: Record<string, string> = {}
  if (parentPath) {
    params.parentPath = parentPath
  }
  if (isAdapt) {
    params.isAdapt = isAdapt
  }
  return scanRequest<DirectoryNodeDTO[]>(`/cscan/${taskId}/tree`, {
    method: 'GET',
    params,
  })
}
