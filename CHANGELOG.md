
# Changelog

本项目全部 notable changes 均记录于本文件。

格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

> 版本口径：MAJOR.MINOR.PATCH。MAJOR 为不兼容的 API / 迁移行为变更，MINOR 为向后兼容的功能新增或废弃预告，PATCH 为向后兼容的缺陷修复。涉及知识库、初始化 DDL、迁移脚本的破坏性改动，一律计入 MAJOR。

## [1.0.0] - 2026-09-24

首个公开发布版本。以「知识库初始化 + 批量迁移」为主线，提供从初始化、校验、迁移到应用落地的完整工具链。

### Added

- 仓库文档：`README.md`、`CONTRIBUTING.md`、`CHANGELOG.md`、`.gitignore`，以及木兰宽松许可证第 2 版（MulanPSL-2.0）。
- GitHub 仓库 `rdi-bj/tongming-portkit` 创建并完成首次提交。

### 说明

- 前端与后端源码模块已合入本仓库。本版本的条目用于声明首个正式发布的能力边界，实际内容以发布时的源码为准。
- 本次版本号定为 `1.0.0` 是基于初始发布决定的版本号，不意味着已声明稳定的公开 API。对外调用接口与命令参数在本版本内仍可能调整，兼容性以本文件对应条目为准。
