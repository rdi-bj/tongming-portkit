import type { AppConfig } from '#/app'
import type { DeepPartial } from '#/utils'
import { toMerged } from 'es-toolkit'
import { defaultAppConfig } from './config.default'

const customConfig: DeepPartial<AppConfig> = {}

export const appConfig: AppConfig = toMerged(customConfig, defaultAppConfig)
