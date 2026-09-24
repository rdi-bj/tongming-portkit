<script setup lang="ts">
import AccountLoginForm from './components/account-login-form.vue'

definePage({
  name: 'Login',
  meta: {
    title: 'routes.login',
    layout: 'pure',
    requiresLogin: false,
  },
})

const { t } = useI18n()
</script>

<template>
  <div class="login-container">
    <!-- Left: Brand Panel -->
    <div class="brand-panel">
      <div class="brand-content">
        <svg viewBox="0 0 50 50" class="brand-icon" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <clipPath id="login-brand-clip">
              <path d="m 12,344 h 72 v 56.47 H 12 Z" />
            </clipPath>
          </defs>
          <g
            transform="matrix(1.1941724,0,0,-1.2036551,-31.721997,482.02774)"
            clip-path="url(#login-brand-clip)"
          >
            <path
              d="m 49.92,387.3 c 0,-5.937 -3.726,-11.54 -10.94,-12.77 l 10.16,-11.88 1.014,1.346 18.28,25.65 v 10.87 h -29.57 c 7.334,-1.231 11.06,-7.172 11.06,-13.22 z"
              style="fill: #f6b21a"
            />
            <path
              d="m 55.11,360.5 13.32,18.6 v -20.17 l -14.33,-1.4e-4 z m -25.95,19.49 h 7.108 c 5.081,0 7.677,3.699 7.677,7.283 0,3.588 -2.486,7.287 -7.677,7.287 h -9.704 v -35.63014 h 17.72 L 29.044,377.19 v 2.801 z"
              style="fill: #2a3172"
            />
          </g>
        </svg>
        <h1 class="brand-title">{{ t('app.title') }}</h1>
        <p v-if="t('auth.banner')" class="brand-subtitle">
          {{ t('auth.banner') }}
        </p>
        <!-- Decorative floating shapes -->
        <div class="deco-circle deco-1" />
        <div class="deco-circle deco-2" />
        <div class="deco-circle deco-3" />
      </div>
    </div>

    <!-- Right: Form Panel -->
    <div class="form-panel">
      <div class="header-actions">
        <SwitchColorMode />
        <SwitchLocale />
      </div>

      <div class="form-wrapper">
        <div class="form-header">
          <svg viewBox="0 0 50 50" class="form-logo-icon" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <clipPath id="login-form-clip">
                <path d="m 12,344 h 72 v 56.47 H 12 Z" />
              </clipPath>
            </defs>
            <g
              transform="matrix(1.1941724,0,0,-1.2036551,-31.721997,482.02774)"
              clip-path="url(#login-form-clip)"
            >
              <path
                d="m 49.92,387.3 c 0,-5.937 -3.726,-11.54 -10.94,-12.77 l 10.16,-11.88 1.014,1.346 18.28,25.65 v 10.87 h -29.57 c 7.334,-1.231 11.06,-7.172 11.06,-13.22 z"
                style="fill: #f6b21a"
              />
              <path
                d="m 55.11,360.5 13.32,18.6 v -20.17 l -14.33,-1.4e-4 z m -25.95,19.49 h 7.108 c 5.081,0 7.677,3.699 7.677,7.283 0,3.588 -2.486,7.287 -7.677,7.287 h -9.704 v -35.63014 h 17.72 L 29.044,377.19 v 2.801 z"
                style="fill: #2a3172"
              />
            </g>
          </svg>
          <h2 class="form-title">{{ t('app.title') }}</h2>
        </div>

        <div class="form-card">
          <AccountLoginForm />
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login-container {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

/* ===== Brand Left Panel ===== */
.brand-panel {
  position: relative;
  display: flex;
  flex: 0 0 55%;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(135deg, #0c0c1d 0%, #141432 25%, #0f3460 55%, #533483 80%);
}

.brand-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  text-align: center;
}

.brand-icon {
  width: 96px;
  height: 96px;
  margin-bottom: 1.5rem;
  color: #fff;
  animation: float 3s ease-in-out infinite;
}

.brand-title {
  margin-bottom: 0.75rem;
  font-size: 2rem;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0.02em;
}

.brand-subtitle {
  max-width: 380px;
  font-size: 1.05rem;
  line-height: 1.6;
  opacity: 0.7;
}

/* Decorative Elements */
.deco-circle {
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
}

.deco-1 {
  top: -180px;
  right: -160px;
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, rgba(83, 52, 131, 0.35), transparent 70%);
}

.deco-2 {
  bottom: -100px;
  left: -100px;
  width: 380px;
  height: 380px;
  background: radial-gradient(circle, rgba(15, 52, 96, 0.5), transparent 70%);
}

.deco-3 {
  top: 55%;
  left: 8%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.04), transparent 70%);
}

/* ===== Form Right Panel ===== */
.form-panel {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow-y: auto;
  background: var(--ant-color-bg-layout);
}

.header-actions {
  position: absolute;
  top: 1rem;
  right: 1.5rem;
  z-index: 10;
  display: flex;
  gap: 0.5rem;
}

.form-wrapper {
  width: 100%;
  max-width: 420px;
  padding: 2rem;
  animation: fadeInUp 0.5s ease-out;
}

.form-header {
  margin-bottom: 2rem;
  text-align: center;
}

.form-logo-icon {
  display: block;
  width: 52px;
  height: 52px;
  margin: 0 auto 0.75rem;
}

.form-title {
  margin-bottom: 0.35rem;
  font-size: 1.35rem;
  font-weight: 600;
}

.form-card {
  padding-top: 8px;
}

/* ===== Animations ===== */
@keyframes float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-12px);
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== Responsive ===== */
@media (max-width: 860px) {
  .brand-panel {
    display: none;
  }
}
</style>
