---
name: speckit-plan
description: Create a technical implementation plan from the spec, including a Constitution Check against memory/constitution.md. 读 spec.md + constitution，写 plan.md 含 Constitution Check 合规。
argument-hint: Optional guidance for the planning phase (e.g. tech stack preferences)
user-invocable: true
disable-model-invocation: false
---

# speckit-plan：编写实现计划 plan.md + Constitution Check

本命令把 `spec.md` 翻译成技术实现方案 `plan.md`，并**对照 `memory/constitution.md` 做合规检查**（SDD 的治理门禁）。遵循 spec-kit plan 命令逻辑，适配本项目路径，无 CLI 依赖。

## User Input

```text
$ARGUMENTS
```

你 **必须** 先考虑用户输入（若非空）——通常用户在此给技术栈偏好、架构约束。若空则按项目既有约定推断。

## 前置检查

1. **定位 feature 目录**：扫 `specs/` 下目录，若只有一个待 plan 的（无 plan.md 或最近创建）直接用；若多个，问用户或取 `$ARGUMENTS` 指定的 NNN。设 `FEATURE_DIR=specs/<NNN>-<slug>`。
2. **确认 spec 存在**：`FEATURE_DIR/spec.md` 必须存在且非空，否则报错"无 spec.md，先跑 /speckit-specify"。

## 执行流程

### 1. 加载上下文

- 读 `FEATURE_DIR/spec.md`（功能需求、用户故事、验收标准）。
- **读 `memory/constitution.md`**（治理基线，**必读**——plan 的灵魂是 Constitution Check）。
- 读 `CLAUDE.md` 关键约定段（模块/白名单/菜单/表前缀/提交）。
- 参考 `specs/001-health-check/plan.md` 格式（样板）。

### 2. 写 plan.md（写入 FEATURE_DIR/plan.md）

按以下结构（对齐 `specs/001-health-check/plan.md`）：

```markdown
# Implementation Plan: <功能名>

**Branch**: `<NNN>-<slug>` | **Date**: <今日> | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `FEATURE_DIR/spec.md`

## Summary

<2-4 句话概述：在哪做什么、怎么满足 spec、关键约束。>

## Technical Context

- **Language/Version**: Java 17 / Spring Boot 4.1.0
- **Primary Dependencies**: <如 ruoyi-common 提供的 AjaxResult/@Anonymous；新依赖说明用途>
- **Storage**: <数据库？涉及哪表，必须 ai_devops_ 前缀 + 审计字段；无则 N/A>
- **Testing**: <JUnit5？手动验证？REST-Assured？说明测试策略>
- **Target Platform**: <Linux/macOS，内嵌 Tomcat 8080>
- **Project Type**: web-service（Maven 多模块）
- **Constraints**: <必须满足的约束，如免登录、不改上游、不改 Security>

## Design

### <子节：数据模型 / API 契约 / 关键算法 / 文件结构>

<按需写：涉及 DB 则 data-model 段（表 ai_devops_xxx + 审计字段 create_by/create_time/update_by/update_time/del_flag）；涉及 API 则契约段；简单功能可省略子节，只写文件落点。>

**文件落点**（遵循 constitution 原则 I + IV，落 ai-devops 模块功能域子包）：
- `ai-devops/src/main/java/com/ruoyi/aidevops/controller/<module>/XxxController.java`
- `ai-devops/src/main/java/com/ruoyi/aidevops/service/<module>/...`
- `ai-devops/src/main/resources/db/migration/V{N}__<desc>.sql`（若涉及表，V3 起编号）

## Constitution Check

*GATE: Must pass before implementation. Re-check after design.*

对照 `memory/constitution.md` Principles I–VI + Security 段：

| Principle | Pass | 说明 |
|-----------|------|------|
| I. 模块隔离与上游零冲突 | ✅/❌/N/A | <代码落 ai-devops 模块，是否改上游源码> |
| II. 注解化白名单 | ✅/❌/N/A | <免登录是否用 @Anonymous，是否改 SecurityConfig> |
| III. 菜单数据驱动 | ✅/❌/N/A | <有菜单是否走 sys_menu + 动态路由，是否改静态路由> |
| IV. 分层单向依赖 | ✅/❌/N/A | <是否 Controller→Service→Mapper→Domain 单向，功能域子包> |
| V. 单体多模块与简单性 | ✅/❌/N/A | <是否无过度抽象，未额外拆分> |
| VI. API 语义契约 | ✅/❌/N/A | <HTTP 方法是否对齐操作语义：查询 GET / 创建·动作 POST / 修改 PUT / 删除 DELETE；有副作用操作是否禁用 GET> |

**凭据治理**（Security 段）：
- <密码是否 ${ENV_VAR:默认值} 占位；表是否 ai_devops_ 前缀；结构变更是否走 Flyway>

**结论**：<全部通过 / 有违规需豁免>。

> 违规处理：与 MUST 冲突的，**改 spec/plan 解决，不是稀释原则**。若有不得不的偏离，填下表并在 PR 说明：

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| （无/说明） | — | — |

## Implementation Phases

<简述实现分几阶段、关键依赖顺序，供 /speckit-tasks 拆任务参考。>

## Assumptions / Open Questions

- <推断的默认决策、待确认问题>
```

### 3. 合规校验（强制）

- Constitution Check 表必须**逐条**填，不许跳过（N/A 也要写理由）。
- 任何 ❌ 或偏离 MUST 项的，必须在违规豁免表给出理由，否则 plan 视为未通过——回去改 spec 或 plan 自身。
- 完成后**重新评估** Constitution Check（design 后复查）。

### 4. 完成报告

报告：`plan.md` 路径、Constitution Check 结论（通过/违规豁免）、ready for tasks。

## 下一步建议

plan 通过合规后，运行 **`/speckit-tasks`** 把 plan 拆成可执行任务清单（标 [P] 并行）。
