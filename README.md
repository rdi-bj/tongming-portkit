# RISC-V 专用 AI 跨平台移植工具

---

## 📋 项目简介

响应芯片产业国产化与多元化发展趋势，针对RISC-V架构在软件生态建设中面临的"跨架构代码移植"核心堵点，构建一个智能化、自动化的跨架构（X86/ARM到RISC-V）移植分析与移植建议工具。

---

## 🚀 环境要求

- JDK 21+
- Node.js 18+
- MySQL 8.0+
- Redis
- RabbitMQ

---

## 🏗️ 技术栈

| 层       | 技术                                                         |
| -------- | ------------------------------------------------------------ |
| 前端     | **Vue3** + Element Plus                                |
| 后端     | **SpringBoot3** + Java 21                              |
| 数据库   | **MySQL** 8.0                                          |
| 代码解析 | **tree-sitter**（多语言语法树解析）                    |
| 接口调用 | **retrofit-spring-boot-starter**（声明式 HTTP 客户端） |

---

## ⚡ 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/rdi-bj/tongming-portkit
cd https://github.com/rdi-bj/tongming-portkit
```

### 2. 基础中间件准备

启动项目依赖的中间件（MySQL / Redis / RabbitMQ）。推荐使用 Docker 一键拉起：

```bash
docker run -d --name mysql   -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8.0
docker run -d --name redis   -p 6379:6379 redis
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:management
```

- **MySQL**：创建数据库 `scan_db`，并执行 `backend/sql/init.sql`（如有）
- **RabbitMQ**：管理界面 `http://localhost:15672`（默认账号 `guest/guest`）

### 3. 后端启动（SpringBoot3）

#### 3.1 配置修改

进入 `backend/scan-launcher/src/main/resources/`，根据环境复制并修改配置：

主要修改项：

- `spring.datasource`：MySQL 连接地址、账号、密码
- `spring.data.redis`：Redis 连接
- `spring.rabbitmq`：RabbitMQ 连接
- `llm`：大模型 API 地址与密钥（如启用 AI 适配）

#### 3.2 启动 Master（scan-launcher）

```bash
cd backend/scan-launcher
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,web,worker,corpus
```

> 也可在 IDE 中直接运行 `ScanApplication.java`。

#### 3.3 启动 Slave（scan-worker）

```bash
cd backend/scan-worker
./mvnw spring-boot:run
```

Worker 启动后会自动连接 RabbitMQ 并监听任务队列，等待 Master 分发任务。

### 4. 前端启动（Vue3 + Vite）

#### 4.1 环境准备

- **Node.js 18+**
- 推荐使用 `pnpm`：`npm install -g pnpm`

#### 4.2 安装依赖

```bash
cd frontend
pnpm install
```

#### 4.3 配置后端代理

检查 `frontend/vite.config.ts` 中的代理设置，确保三个后端代理前缀指向正确的后端地址（默认 `http://localhost:8080`）。如需修改接口地址，可编辑 `.env.development` 中的 `VITE_API_BASE_URL`。

#### 4.4 启动开发服务器

```bash
pnpm dev
```

浏览器访问 `http://localhost:5173`（具体端口以终端输出为准）。

### 5. 验证启动

- 前端登录页正常加载（默认账号见数据库初始化脚本）
- 进入「迁移检测」页面，上传源码包测试快速扫描
- 观察后端日志，确认任务经 RabbitMQ 成功分发至 Worker 执行

### 6. Docker 一键启动（可选）

如需容器化部署，使用项目根目录的 `docker-compose.yml`（如有）：

```bash
docker-compose up -d
```

---

### 📌 常见问题

- **端口冲突**：修改 `application.yml` 中 `server.port` 或前端 `.env.development` 的 `VITE_API_BASE_URL`。
- **RabbitMQ 连接失败**：检查 `application-mq.yml` 配置，确认 RabbitMQ 服务已启动。
- **AI 适配无响应**：确认 `application-corpus.yml` 中大模型配置正确且网络可达。

---

## 📁 项目结构

项目采用 **前后端分离** 架构，后端基于 SpringBoot3 构建，以 **Master/Slave（主从）** 模式协作：`scan-launcher` 为 Master 端（任务调度/管理），`scan-worker` 为 Slave 端（任务执行），两者通过 **RabbitMQ** 进行任务发布与通信。

### 后端结构

```
backend/
├── scan-launcher/                   # Master 端：任务调度与下发给 Worker
│   ├── src/main/java/com/jinw/ScanApplication.java
│   └── src/main/resources/
│       ├── application.yml            # 主配置（含 RabbitMQ）
│       ├── application-dev.yml        # 开发环境
│       ├── application-prod.yml       # 生产环境
│       ├── application-web.yml        # Web 相关配置
│       ├── application-worker.yml     # Worker 相关配置
│       ├── application-docker.yml     # Docker 部署配置
│       ├── application-corpus.yml     # 语料库配置
│       └── application-all.yml        # 全量聚合配置
├── scan-worker/                     # Slave 端：消费任务并执行代码扫描/适配
│   └── src/main/java/com/jinw/worker/
│       ├── config/                    # 配置
│       ├── consumer/                  # RabbitMQ 任务消费者
│       ├── cdt2/                      # CDT 解析
│       ├── ast/                       # AST 语法树解析（c/cpp）
│       ├── llm/                       # 大模型接入（agent、config、utils）
│       └── log/                       # 日志
├── scan-common/                     # 公共模块（domain、constant、enums、utils）
├── scan-mq/                         # 消息队列模块（RabbitMQ 发布/订阅）
├── scan-web/                        # Web 接口模块（controller、service、mapper）
└── scan-corpus/                     # 语料库模块（RISC-V 知识库）
```

### 前端结构

```
frontend/
├── src/                         # 前端源码（Vue 3 + TS + Vite）
│   ├── api/                     # 接口层（对应后端 scan-web 的 HTTP 调用侧）
│   │   ├── index.ts             # alova 实例（/proxy-api，通用后台接口，含 mock 适配器）
│   │   ├── scan.ts              # Scanner 接口：项目 CRUD、启动扫描、进度/文件树/报告
│   │   ├── llm.ts               # LLM 接口：模型配置、文件/项目级适配、适配结果、项目树
│   │   ├── kingow.ts            # Kingow 商业版接口：登录、用户、权限
│   │   ├── auth.ts              # 认证接口（OSS 开源版 /sys/user/login）
│   │   └── mock/                # @alova/mock 模拟数据（开发环境 VITE_API_ENABLE_MOCK=true）
│   ├── components/              # 组件层
│   │   ├── scan/                # ★ 扫描业务组件
│   │   │   ├── ScanProgressDialog.vue    # 检测进度弹窗（解压/扫描/AI 复核逐文件状态）
│   │   │   ├── ScanReportDialog.vue      # 在线检测报告弹窗（与离线导出报告格式对齐）
│   │   │   ├── AtlasPanel.vue            # 迁移画像面板（ECharts：语言/汇编/依赖/构建）
│   │   │   ├── HomePageAtlas.vue         # 首页画像（拉取画像数据并组织 AtlasPanel）
│   │   │   └── ScanDemoDialog.vue        # 扫描演示弹窗（配合 simulate-quick-scan 调试用）
│   │   ├── light/               # 轻量通用组件（LightButton/Card/Dialog/Tabs...）
│   │   ├── internal/            # 框架内部组件（Logo、主题/语言/折叠开关、UI Provider）
│   │   ├── demo/                # 模板演示组件（调研表、检测报告、进度示例等）
│   │   ├── GraphCanvas.vue      # 自研 Canvas 函数调用/依赖关系图
│   │   └── GraphCanvasForce3D.vue  # 3D force-graph 关系图（three.js）
│   ├── composables/             # 逻辑编排层（对应后端 scan-mq 的消费侧）
│   │   ├── useScanWebSocket.ts      # 单任务扫描进度 WS（心跳15s+指数退避重连）
│   │   ├── useScanListProgressWs.ts # 列表页多任务进度 WS（替代 HTTP 轮询）
│   │   ├── usePolling.ts            # 通用轮询
│   │   ├── useTypewriter.ts         # 打字机效果（适配方案一次性显示后保留）
│   │   ├── useAtlasData.ts          # 迁移画像数据聚合/视觉色调
│   │   └── useGraphScene.ts         # 关系图 Three.js 场景
│   ├── pages/                   # 页面层（文件路由自动生成，definePage 声明元数据）
│   │   ├── dashboard/index.vue  # 首页/迁移画像总览
│   │   ├── scan/projects.vue    # ★ 迁移检测（项目列表/卡片、快速/深度扫描入口）
│   │   ├── llm/adapt/index.vue  # ★ AI 适配（项目/文件级适配、适配后代码查看）
│   │   ├── graph/index.vue      # 关系图（函数调用图 2D/3D）
│   │   ├── system/models/       # 大模型配置管理
│   │   ├── login/               # 登录页（Kingow/OSS 双模式）
│   │   ├── error/               # 403/404 错误页
│   │   ├── demo/                # 模板遗留演示页（调研表、快捷接入、组件演示）
│   │   ├── form/ permission/    # 模板遗留示例页
│   │   └── [...path].vue        # 404 兜底路由
│   ├── layouts/                 # 布局壳（default=管理后台，pure=登录等纯净页）
│   ├── layout-blocks/           # 布局预设块（top/side/mix）
│   ├── layout-components/       # 布局组成件（header/sider/menu/breadcrumb/content/footer）
│   ├── stores/                  # 状态层（Pinia：app 应用配置、user 用户/权限、routes 路由）
│   ├── utils/                   # 基础设施（对应后端 scan-common）
│   │   ├── scan-request.ts      # Scanner 专用 alova 实例 + 文件下载（blob/Content-Disposition）
│   │   ├── kingow-auth.ts       # token 存取、记住密码
│   │   └── element.ts / event-bus.ts / model.ts / promiseModels.ts / utils.ts
│   ├── lib/                     # 图数据与演示数据（edgesDataLoader、forceSimulation、graphData、demoData）
│   ├── hooks/                   # 通用 hooks（useECharts）
│   ├── constants/               # 常量（app 配置、regex 正则）
│   ├── types/                   # 领域类型（对应后端 domain）：scan/llm/api/pages/app/router...
│   │   └── generated/           # 自动生成（auto-imports、components、typed-router）
│   ├── theme/                   # 主题（light/dark 色板）
│   ├── styles/                  # 全局样式（global、atlas、view-transition）
│   ├── plugins/                 # 应用装配（对应后端 application-*.yml 聚合配置）
│   │   ├── index.ts             # 装配入口：store→权限→i18n→router
│   │   └── router.ts / i18n.ts / store.ts / progress-bar.ts / echarts.ts
│   ├── config.default.ts        # 默认应用配置 AppConfig
│   └── main.ts                  # 应用入口
├── build/                       # Vite 构建配置模块（index 组装插件/postcss/路径解析）
├── locales/                     # 国际化（zh-CN / en-US / zh-TW）
├── docs/                        # OpenAPI 接口文档快照（api-docs.v1~v7）
├── scripts/                     # 辅助脚本
│   ├── simulate-quick-scan.mjs  # 快速扫描 WS 消息模拟器（调试进度弹窗）
│   └── find-i18n-leaks.mjs      # i18n key 泄漏检查
├── public/                      # 静态资源（favicon、_redirects）
└── 根配置                        # 相当于后端各 application.yml
    ├── vite.config.ts           # 主配置：代理、别名、构建（含三个后端代理前缀）
    ├── uno.config.ts            # UnoCSS 原子化样式
    ├── tsconfig*.json           # TS 工程配置
    ├── eslint/stylelint/prettier/commitlint
    └── .env.development / .env.test / .env.production
```

### 任务通信机制

`scan-launcher`（Master）通过 RabbitMQ 向 `scan-worker`（Slave）发布扫描/适配任务，Worker 消费任务执行分析后返回结果，实现主从分布式任务处理。

---

## 🔧 核心功能

### 一、智能移植检测与迁移画像

上传源码包一键启动检测，自动生成可视化"迁移画像"报告，精准评估移植风险与改造路线：

| 模式               | 说明                                                       |
| ------------------ | ---------------------------------------------------------- |
| **快速检测** | 一键启动，快速响应，适用于初步评估                         |
| **深度检测** | 全量覆盖所有静态分析发现的问题，适用于对准确度要求高的场景 |

**检测流程：** 项目信息填写 → 源码包上传 → 源码 AST 解析 → 知识库匹配 → RISC-V 问题记录 → AI 复核 → 报告生成

### 二、AI 驱动的代码适配建议

AI 自动判断适配场景，基于垂域大模型生成精准适配策略，分为三个核心场景：

#### 场景一：原位修改

AI 直接在原文件中修改不兼容代码，通过条件编译等手段保留多架构兼容。适用于通用逻辑适配场景。

#### 场景二：新增架构文件

AI 识别现有架构文件模式，自动新建 `riscv_impl.c` 等独立文件，保持项目结构一致。适用于已有 X86/ARM 实现的项目。

#### 场景三：服务器配置描述

AI 自动生成服务器配置描述文件，涵盖编译选项、依赖库、运行时参数等关键信息。适用于基础设施/部署适配场景。

---

## 🏛️ 系统架构

![AI适配结果](images/architecture.png)

### 技术创新点

#### 技术创新一：代码拆分技术

通过智能代码拆分，将大型项目分解为可管理的子任务单元，结合知识库特征精准匹配，实现从开源项目到 RISC-V 适配的全流程自动化语料采集。

#### 技术创新二：AI Agent 驱动的自动化流程

传统人工模式依赖专家经验，流程易中断、效率低、成本高。本工具采用AI Agent实现从代码分析到适配的端到端自动化闭环，大幅缩短移植周期。

---

## 🖼️ 软件使用截图

以下截图展示了工具的核心界面与功能流程。

### 首页

![首页](images/home.png)

### 检测准备

![检测准备](images/detection-ready.png)

### 检测列表

![检测列表](images/detection-list.png)

### 检测进度

![检测进度](images/detection-progress.png)

### 检测问题查看

![检测问题查看](images/detection-issue-view.png)

### 在线报告

![在线报告](images/online-report.png)

### AI 适配过程

![AI适配过程](images/ai-adaptation-process.png)

### AI 适配结果

![AI适配结果](images/ai-adaptation-result.png)

### 模型维护

![模型维护](images/model-management.png)

## 许可证

`tongming-portkit` 版权归 **北京瑞偲创新科技产业有限公司** 所有，基于 **木兰宽松许可证第 2 版（Mulan PSL v2）** 开源。

- 许可证全文：http://license.coscl.org.cn/MulanPSL2
- SPDX 标识：`MulanPSL-2.0`
