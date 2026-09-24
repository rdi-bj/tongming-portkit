<script setup lang="ts">
import type { ColProps } from 'antdv-next'
import type { BTLlmConfig } from '@/types/llm'
import { Modal } from 'antdv-next'
import {
  changeLlmConfig,
  createLlmConfig,
  deleteLlmConfig,
  getLlmConfigDetail,
  getLlmConfigPage,
  updateLlmConfig,
} from '@/api/llm'

const { t } = useI18n()

/**
 * `wrapperCol` is forwarded to the rendered <Col>, but antdv's ColProps type omits
 * `style`; widen it locally so the inline margin stays typed.
 */
const dialogActionsCol: ColProps & { style: Record<string, string> } = {
  style: { marginLeft: '180px' },
}
definePage({
  name: 'SystemModels',
  meta: {
    title: 'routes.system.models',
    menu: true,
    menuSort: 10,
  },
})

// --- List state ---
const configs = ref<BTLlmConfig[]>([])
const loading = ref(false)
const keyword = ref('')

// --- Pagination ---
const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total: number) => t('llm.config.total-rows', { total }),
})

// --- Table columns ---
const tableColumns = [
  {
    title: t('llm.config.title-column'),
    dataIndex: 'configName',
    key: 'configName',
    minWidth: 160,
  },
  { title: t('llm.config.model-column'), dataIndex: 'llmModel', key: 'llmModel', minWidth: 140 },
  {
    title: t('llm.config.url-column'),
    dataIndex: 'llmUrl',
    key: 'llmUrl',
    minWidth: 200,
    ellipsis: true,
  },
  {
    title: t('llm.config.enabled-column'),
    key: 'enabled',
    width: 90,
  },
  { title: t('llm.config.actions-column'), key: 'action', width: 120 },
]

// --- Create/Edit dialog ---
const dialogVisible = ref(false)
const editingConfigId = ref<string | null>(null)
const submitting = ref(false)
const togglingId = ref<string | null>(null)
const formRef = ref()
const advancedOpen = ref(false)
const configForm = ref({
  configName: '',
  llmUrl: '',
  llmModel: '',
  apiKey: '',
  llmTemperature: 0.2,
  llmTopP: 0.3,
  contextLimit: 262144,
  outputLimit: 131702,
  thinkingBudgetTokens: 8192,
  remark: '',
})

function formToPayload(): Record<string, unknown> {
  return {
    ...(editingConfigId.value ? { id: editingConfigId.value } : {}),
    configName: configForm.value.configName.trim(),
    llmUrl: configForm.value.llmUrl.trim(),
    llmModel: configForm.value.llmModel.trim(),
    apiKey: configForm.value.apiKey || undefined,
    llmTemperature: configForm.value.llmTemperature,
    llmTopP: configForm.value.llmTopP,
    contextLimit: configForm.value.contextLimit,
    outputLimit: configForm.value.outputLimit,
    thinkingBudgetTokens: configForm.value.thinkingBudgetTokens,
    remark: configForm.value.remark.trim() || undefined,
  }
}

async function loadConfigs() {
  loading.value = true
  try {
    const result = await getLlmConfigPage(
      pagination.current,
      pagination.pageSize,
      keyword.value || undefined,
    )
    configs.value = result.records
    pagination.total = result.total
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('llm.messages.load-failed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadConfigs()
}

function handlePageChange(page: number, pageSize: number) {
  pagination.current = page
  pagination.pageSize = pageSize
  loadConfigs()
}

function resetForm() {
  configForm.value = {
    configName: '',
    llmUrl: '',
    llmModel: '',
    apiKey: '',
    llmTemperature: 0.2,
    llmTopP: 0.3,
    contextLimit: 262144,
    outputLimit: 131702,
    thinkingBudgetTokens: 8192,
    remark: '',
  }
  advancedOpen.value = false
}

function openCreateDialog() {
  editingConfigId.value = null
  resetForm()
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function openEditDialog(row: BTLlmConfig) {
  try {
    const detail = await getLlmConfigDetail(row.id)
    editingConfigId.value = detail.id
    configForm.value = {
      configName: detail.configName,
      llmUrl: detail.llmUrl,
      llmModel: detail.llmModel ?? '',
      apiKey: detail.apiKey ?? '',
      llmTemperature: detail.llmTemperature ?? 0.2,
      llmTopP: detail.llmTopP ?? 0.3,
      contextLimit: detail.contextLimit ?? 262144,
      outputLimit: detail.outputLimit ?? 131702,
      thinkingBudgetTokens: detail.thinkingBudgetTokens ?? 8192,
      remark: detail.remark ?? '',
    }
    formRef.value?.clearValidate()
    dialogVisible.value = true
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('llm.messages.detail-failed'))
  }
}

async function submitConfig() {
  if (!configForm.value.configName.trim()) {
    window.$message.warning(t('llm.messages.name-required'))
    return
  }
  if (!configForm.value.llmUrl.trim()) {
    window.$message.warning(t('llm.messages.url-required'))
    return
  }
  if (!configForm.value.llmModel.trim()) {
    window.$message.warning(t('llm.messages.model-required'))
    return
  }
  if (
    configForm.value.llmTemperature == null ||
    configForm.value.llmTopP == null ||
    configForm.value.contextLimit == null ||
    configForm.value.outputLimit == null ||
    configForm.value.thinkingBudgetTokens == null
  ) {
    window.$message.warning(t('llm.messages.advanced-required'))
    return
  }
  submitting.value = true
  try {
    const payload = formToPayload()
    if (editingConfigId.value) {
      await updateLlmConfig(payload)
      window.$message.success(t('llm.messages.update-success'))
    } else {
      await createLlmConfig(payload)
      window.$message.success(t('llm.messages.create-success'))
    }
    dialogVisible.value = false
    editingConfigId.value = null
    resetForm()
    await loadConfigs()
  } catch (error) {
    window.$message.error(
      error instanceof Error
        ? error.message
        : editingConfigId.value
          ? t('llm.messages.update-failed')
          : t('llm.messages.create-failed'),
    )
  } finally {
    submitting.value = false
  }
}

async function removeConfig(row: BTLlmConfig) {
  Modal.confirm({
    title: t('llm.config.delete-title'),
    content: t('llm.config.delete-confirm', { name: row.configName }),
    okText: t('llm.config.delete-ok'),
    cancelText: t('llm.config.cancel-text'),
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteLlmConfig(row.id)
        window.$message.success(t('llm.messages.delete-success'))
        await loadConfigs()
      } catch (error) {
        window.$message.error(
          error instanceof Error ? error.message : t('llm.messages.delete-failed'),
        )
      }
    },
  })
}

async function toggleEnabled(row: BTLlmConfig) {
  // Hidden rule: always keep at least one enabled model
  if (row.enabled === '1') {
    const enabledCount = configs.value.filter((c) => c.enabled === '1').length
    if (enabledCount <= 1) {
      window.$message.warning(t('llm.config.cannot-disable-last'))
      return
    }
  }
  togglingId.value = row.id
  try {
    await changeLlmConfig(row.id)
    window.$message.success(
      row.enabled === '1' ? t('llm.config.disable-success') : t('llm.config.enable-success'),
    )
    await loadConfigs()
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('llm.messages.toggle-failed'))
  } finally {
    togglingId.value = null
  }
}

loadConfigs()
</script>

<template>
  <div>
    <!-- Top bar -->
    <div
      style="
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 30px;
      "
    >
      <AButton type="primary" @click="openCreateDialog">
        {{ t('llm.config.create-config') }}
      </AButton>
      <div style="display: flex; gap: 12px">
        <AInput
          v-model:value="keyword"
          :placeholder="t('llm.config.search-placeholder')"
          allow-clear
          style="width: 300px"
          @press-enter="handleSearch"
        />
        <AButton @click="handleSearch">{{ t('llm.config.search') }}</AButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="py-12 text-center">
      <ASpin size="large" />
    </div>

    <!-- Empty -->
    <ACard v-else-if="configs.length === 0" class="py-20 text-center border-0 w-full shadow-none">
      <div class="mb-6 flex justify-center">
        <RenderIcon
          v-if="keyword"
          icon="i-ant-design:search-outlined"
          class="text-slate-200 size-24"
        />
        <RenderIcon v-else icon="i-ant-design:inbox-outlined" class="text-slate-200 size-24" />
      </div>
      <div class="text-slate-400" style="margin-bottom: 12px; font-size: 22px; font-weight: 600">
        {{ keyword ? t('llm.config.no-search-results') : t('llm.config.no-configs') }}
      </div>
      <div class="text-slate-400" style="font-size: 15px">
        {{ keyword ? t('llm.config.no-search-results-hint') : t('llm.config.no-configs-hint') }}
      </div>
    </ACard>

    <!-- Table -->
    <ATable
      v-else
      :data-source="configs"
      :columns="tableColumns"
      row-key="id"
      size="middle"
      :pagination="{
        ...pagination,
        onChange: handlePageChange,
        onShowSizeChange: handlePageChange,
      }"
    >
      <!-- antdv types this slot's `record` as AnyObject no matter the row type,
           so keep it loose here; the handlers below keep their own row signatures -->
      <template #bodyCell="{ column, record }: { column: any; record: any }">
        <template v-if="column.key === 'enabled'">
          <ASwitch
            :checked="record.enabled === '1'"
            :loading="togglingId === record.id"
            size="small"
            @change="toggleEnabled(record)"
          />
        </template>
        <template v-else-if="column.key === 'action'">
          <ASpace :size="4">
            <AButton type="link" size="small" @click="openEditDialog(record)">
              {{ t('llm.config.edit') }}
            </AButton>
            <AButton type="link" size="small" danger @click="removeConfig(record)">
              {{ t('llm.config.delete') }}
            </AButton>
          </ASpace>
        </template>
      </template>
    </ATable>

    <!-- Create/Edit dialog -->
    <LightDialog
      v-model:open="dialogVisible"
      width="1000px"
      height="auto"
      min-height="520px"
      padding="20px 30px 30px 30px"
    >
      <template #title>{{
        editingConfigId ? t('llm.config.edit-dialog-title') : t('llm.config.create-dialog-title')
      }}</template>
      <div class="create-form">
        <LightCard style="flex: 1; padding: 40px 30px; border-radius: 10px">
          <AForm
            ref="formRef"
            :model="configForm"
            style="max-width: 900px; margin: 0 auto"
            layout="horizontal"
            :label-col="{ style: { width: '180px' } }"
            :wrapper-col="{ style: { flex: 1 } }"
            @finish="submitConfig"
          >
            <!-- ====== Basic configuration ====== -->
            <AFormItem
              :label="t('llm.config.configName-label')"
              name="configName"
              required
              style="margin-bottom: 28px"
            >
              <AInput
                v-model:value="configForm.configName"
                :placeholder="t('llm.config.configName-placeholder')"
                size="large"
              />
            </AFormItem>

            <AFormItem
              :label="t('llm.config.llmUrl-label')"
              name="llmUrl"
              required
              style="margin-bottom: 28px"
            >
              <AInput
                v-model:value="configForm.llmUrl"
                :placeholder="t('llm.config.llmUrl-placeholder')"
                size="large"
              />
            </AFormItem>

            <AFormItem
              :label="t('llm.config.llmModel-label')"
              name="llmModel"
              required
              style="margin-bottom: 28px"
            >
              <AInput
                v-model:value="configForm.llmModel"
                :placeholder="t('llm.config.llmModel-placeholder')"
                size="large"
              />
            </AFormItem>

            <AFormItem :label="t('llm.config.apiKey-label')" style="margin-bottom: 28px">
              <AInputPassword
                v-model:value="configForm.apiKey"
                :placeholder="t('llm.config.apiKey-placeholder')"
                size="large"
              />
            </AFormItem>

            <!-- ====== Remarks ====== -->
            <AFormItem :label="t('llm.config.remark-label')" style="margin-bottom: 28px">
              <ATextarea
                v-model:value="configForm.remark"
                :rows="3"
                :placeholder="t('llm.config.remark-placeholder')"
              />
            </AFormItem>

            <!-- ====== Advanced configuration (collapsible) ====== -->
            <div
              class="advanced-toggle"
              style="
                display: flex;
                align-items: center;
                padding-bottom: 16px;
                margin-bottom: 0;
                cursor: pointer;
                user-select: none;
                border-bottom: 1px solid #f0f0f0;
              "
              @click="advancedOpen = !advancedOpen"
            >
              <span style="font-size: 15px; font-weight: 600; color: #3578ff">
                {{
                  advancedOpen
                    ? t('llm.config.advanced-config-hide')
                    : t('llm.config.advanced-config-show')
                }}
              </span>
            </div>

            <div v-show="advancedOpen">
              <AFormItem
                :label="t('llm.config.llmTemperature-label')"
                name="llmTemperature"
                required
                style="margin-top: 28px; margin-bottom: 28px"
              >
                <AInputNumber
                  v-model:value="configForm.llmTemperature"
                  :placeholder="t('llm.config.llmTemperature-placeholder')"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  size="large"
                  style="width: 200px"
                />
              </AFormItem>

              <AFormItem
                :label="t('llm.config.llmTopP-label')"
                name="llmTopP"
                required
                style="margin-bottom: 28px"
              >
                <AInputNumber
                  v-model:value="configForm.llmTopP"
                  :placeholder="t('llm.config.llmTopP-placeholder')"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  size="large"
                  style="width: 200px"
                />
              </AFormItem>

              <AFormItem
                :label="t('llm.config.contextLimit-label')"
                name="contextLimit"
                required
                style="margin-bottom: 28px"
              >
                <AInputNumber
                  v-model:value="configForm.contextLimit"
                  :placeholder="t('llm.config.contextLimit-placeholder')"
                  :min="1"
                  :step="1000"
                  size="large"
                  style="width: 200px"
                />
              </AFormItem>

              <AFormItem
                :label="t('llm.config.outputLimit-label')"
                name="outputLimit"
                required
                style="margin-bottom: 28px"
              >
                <AInputNumber
                  v-model:value="configForm.outputLimit"
                  :placeholder="t('llm.config.outputLimit-placeholder')"
                  :min="1"
                  :step="1000"
                  size="large"
                  style="width: 200px"
                />
              </AFormItem>

              <AFormItem
                :label="t('llm.config.thinkingBudgetTokens-label')"
                name="thinkingBudgetTokens"
                required
                style="margin-bottom: 28px"
              >
                <AInputNumber
                  v-model:value="configForm.thinkingBudgetTokens"
                  :placeholder="t('llm.config.thinkingBudgetTokens-placeholder')"
                  :min="0"
                  :step="100"
                  size="large"
                  style="width: 200px"
                />
              </AFormItem>
            </div>

            <AFormItem :wrapper-col="dialogActionsCol">
              <div style="display: flex; gap: 12px; justify-content: flex-end">
                <AButton size="large" @click="dialogVisible = false">
                  {{ t('llm.config.cancel') }}
                </AButton>
                <AButton type="primary" size="large" :loading="submitting" html-type="submit">
                  {{ t('llm.config.save') }}
                </AButton>
              </div>
            </AFormItem>
          </AForm>
        </LightCard>
      </div>
    </LightDialog>
  </div>
</template>

<style lang="scss" scoped>
.create-form {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.advanced-toggle {
  padding-top: 8px;
  padding-left: 8px;
  transition: background 0.15s;

  &:hover {
    background: rgba(53, 120, 255, 0.04);
    border-radius: 6px;
  }
}
</style>

<style lang="scss">
.dark {
  .text-slate-400 {
    color: #a0adde !important;
  }

  .advanced-toggle {
    border-bottom-color: #2a3050 !important;

    &:hover {
      background: rgba(53, 120, 255, 0.08) !important;
    }
  }
}
</style>
