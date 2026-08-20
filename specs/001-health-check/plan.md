# Implementation Plan: 健康检查接口

**Branch**: `001-health-check` | **Date**: 2026-08-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-health-check/spec.md`

**Note**: 本文件按 spec-kit `templates/plan-template.md` 结构填写（手写，非 CLI 生成）。

## Summary

在新建的 `ai-devops` Maven 模块内，提供一个 `GET /aidevops/health` Controller，用 `@Anonymous` 注解加入白名单，返回若依标准 `AjaxResult`。通过根 pom 注册模块 + admin pom 引依赖，让启动类默认扫描到 `com.ruoyi.aidevops.controller` 包。无需改启动类、无需改 Security、无需数据库。

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Spring Boot 4.1.0（Web MVC）、`ruoyi-common`（提供 `AjaxResult`、`@Anonymous`）

**Storage**: N/A（本功能无 DB 交互）

**Testing**: 手动浏览器验证 + 编译验证（无自动化测试用例）

**Target Platform**: Linux/macOS/Windows，内嵌 Tomcat，端口 8080

**Project Type**: web-service（Maven 多模块）

**Performance Goals**: N/A（轻量接口）

**Constraints**: 必须免登录、不改上游模块、不改 SecurityConfig

**Scale/Scope**: 单接口，2 新增文件 + 2 修改文件

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

对照 `memory/constitution.md` Principles I–V：

| Principle | Pass | 说明 |
|-----------|------|------|
| I. 模块隔离与上游零冲突 | ✅ | Controller 在 `com.ruoyi.aidevops.controller`，未改上游模块源码 |
| II. 注解化白名单 | ✅ | 用 `@Anonymous`，未改 `SecurityConfig` |
| III. 菜单数据驱动 | N/A | 本功能无菜单（免登录接口） |
| IV. 分层单向依赖 | ✅ | 仅 Controller 层（无业务逻辑，无需 Service/Mapper） |
| V. 单体多模块与简单性 | ✅ | 新增 1 个 ai-devops 模块，未额外拆分，无过度抽象 |

**结论**：全部通过，无违规需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/001-health-check/
├── spec.md              # 功能规范
├── plan.md              # 本文件（实施计划）
└── tasks.md             # 任务清单
```

### Source Code (repository root)

```text
AI-DevOps/
├── ai-devops/                                      # 新增模块
│   ├── pom.xml                                     # 新增：parent=ruoyi:3.9.2，依赖 ruoyi-common
│   └── src/main/java/com/ruoyi/aidevops/controller/
│       └── HealthController.java                   # 新增：GET /aidevops/health + @Anonymous
├── pom.xml                                         # 修改：<modules> 加 ai-devops + dependencyManagement 声明版本
└── ruoyi-admin/pom.xml                             # 修改：引入 ai-devops 依赖
```

**Structure Decision**：选择"在 ai-devops 独立模块新增 Controller"而非"塞进 ruoyi-admin"。理由遵循宪法原则 I（模块隔离），ai-devops 目录上游永不触碰，同步上游零冲突。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| （无违规） | — | — |
