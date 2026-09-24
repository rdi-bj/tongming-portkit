<script setup lang="ts">
import type { FormProps } from 'antdv-next'
import type { AccountLoginForm } from '@/types/pages/auth'
import { getSavedCredentials, saveCredentials } from '@/utils/auth-storage'

const { t } = useI18n()

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const formRules: FormProps['rules'] = {
  username: [{ required: true, message: t('auth.username-required') }],
  password: [{ required: true, message: t('auth.password-required') }],
}

const formModel = reactive<AccountLoginForm>({
  username: '',
  password: '',
  remember: false,
})

const loading = ref<boolean>(false)

// Load the remembered account credentials
function loadSaved() {
  const { username, password, rememberMe } = getSavedCredentials()
  if (rememberMe) {
    formModel.username = username
    formModel.password = password
    formModel.remember = true
  }
}

loadSaved()

async function handleSubmit() {
  try {
    loading.value = true
    await userStore.handleLogin(formModel)
    // Store the remembered password
    saveCredentials(formModel.username, formModel.password, formModel.remember)
    window.$message.success(t('auth.login-success'))
    router.replace((route.query.redirect as string) || '/dashboard')
  } catch (error) {
    window.$message.error(
      error instanceof Error ? error.message : t('auth.username-or-password-incorrect'),
    )
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AForm
    size="large"
    class="login-form"
    :model="formModel"
    :rules="formRules"
    @finish="handleSubmit"
  >
    <AFormItem name="username">
      <AInput
        v-model:value="formModel.username"
        :placeholder="t('auth.username')"
        class="login-input"
      >
        <template #prefix>
          <RenderIcon icon="i-ant-design:user-outlined" class="size-5" />
        </template>
      </AInput>
    </AFormItem>
    <AFormItem name="password">
      <AInputPassword
        v-model:value="formModel.password"
        :placeholder="t('auth.password')"
        class="login-input"
      >
        <template #prefix>
          <RenderIcon icon="i-ant-design:lock-outlined" class="size-5" />
        </template>
      </AInputPassword>
    </AFormItem>
    <div class="form-options">
      <AFormItem name="remember" class="no-margin">
        <ACheckbox v-model:checked="formModel.remember">
          {{ t('auth.remember-me') }}
        </ACheckbox>
      </AFormItem>
    </div>
    <AFormItem>
      <AButton :loading type="primary" block html-type="submit" class="login-btn">
        {{ t('auth.login') }}
      </AButton>
    </AFormItem>
  </AForm>
</template>

<style lang="scss" scoped>
.login-form {
  width: 100%;
}

.login-input {
  transition: box-shadow 0.25s ease;

  &:focus,
  &:focus-within {
    box-shadow: 0 0 0 2px rgba(var(--ant-color-primary-rgb, 22, 119, 255), 0.12);
  }
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.5rem;

  .no-margin {
    margin-bottom: 0;
  }
}

.login-btn {
  height: 44px;
  font-size: 1rem;
  border-radius: 6px;
}

/* Dark mode overrides */
:root[class='dark'] {
  .login-input {
    &:focus,
    &:focus-within {
      box-shadow: 0 0 0 2px rgba(var(--ant-color-primary-rgb, 22, 119, 255), 0.25);
    }
  }
}
</style>
