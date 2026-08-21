---
name: speckit-analyze
description: Perform a non-destructive consistency and constitution-compliance analysis across spec.md, plan.md, and tasks.md. 只读检查 spec/plan/tasks 三者一致性 + constitution 合规，产出报告不改文件。
argument-hint: Optional focus areas (e.g. "constitution compliance" or "spec-plan alignment")
user-invocable: true
disable-model-invocation: false
---

# speckit-analyze：跨产物一致性分析（只读）

本命令**只读**检查一个 feature 的 `spec.md` / `plan.md` / `tasks.md` 三者是否一致、有无违背 constitution，产出报告，**不改任何文件**。这是 SDD 的持续精炼环节——tasks 完成后、review 时、或怀疑规范与实现脱节时跑。遵循 spec-kit analyze 命令逻辑，适配本项目，无 CLI 依赖。

## User Input

```text
$ARGUMENTS
```

你 **必须** 先考虑用户输入（若非空）——通常是聚焦域，如"只查 constitution 合规""只查 spec-plan 对齐"。若空则全维度检查。

## 前置检查

1. **定位 feature 目录**：扫 `specs/` 下目录，若只有一个直接用；若多个，问用户或取 `$ARGUMENTS` 指定的 NNN。设 `FEATURE_DIR=specs/<NNN>-<slug>`。
2. **确认产物存在**：列出 `FEATURE_DIR` 下 `spec.md` / `plan.md` / `tasks.md` 哪些存在。缺的报"未生成，先跑对应命令"。
3. 读 `memory/constitution.md`（合规基准）。

## 执行流程

读 `FEATURE_DIR` 下存在的 spec/plan/tasks，逐维度检查，产出一份**只读分析报告**（显示给用户，不写入文件，除非用户要求存档）：

### 维度 1：spec ↔ plan 对齐

- plan 的 Summary/Technical Context 是否覆盖 spec 的所有 Functional Requirements（FR-xxx）？
- plan 的文件落点是否满足 spec 的 Key Entities（表名 `ai_devops_` 前缀、审计字段）？
- spec 的 Success Criteria 是否在 plan 有对应的验证手段？
- spec 有而 plan 漏的需求？（plan 未覆盖的 FR 标 ⚠️）

### 维度 2：plan ↔ tasks 对齐

- tasks 是否覆盖 plan 的所有 Implementation Phases？
- tasks 的文件路径是否与 plan 的文件落点一致？
- plan 标注的 CRITICAL 依赖（如 Foundational 阻塞）是否在 tasks 体现？
- tasks 有而 plan 未设计的任务？（tasks 超出 plan 范围标 ⚠️，可能 plan 不全）

### 维度 3：spec ↔ tasks 对齐

- 每个 spec 的 user story 是否在 tasks 有对应 Phase / 任务组？
- tasks 的 [Story] 标签是否映射到真实存在的 spec user story？
- spec 的验收场景能否在 tasks 找到验证任务？

### 维度 4：constitution 合规（最重要）

对照 `memory/constitution.md` 五原则 + Security 段，逐条查 spec/plan/tasks：

| 原则 | 检查点 | 通过？ |
|---|---|---|
| I 模块隔离 | plan 文件落点是否都在 `ai-devops` 模块？有无碰上游模块？ | ✅/⚠️ |
| II 注解白名单 | 免登录需求是否用 `@Anonymous`？有无改 `SecurityConfig` 的迹象？ | ✅/⚠️ |
| III 菜单数据驱动 | 新增菜单需求是否走 `sys_menu`？有无改前端静态路由？ | ✅/⚠️/N/A |
| IV 分层依赖 | plan 文件结构是否 Controller→Service→Mapper→Domain 单向？功能域子包？ | ✅/⚠️ |
| V 简单性 | 有无过度抽象、未用的扩展点、无端拆分？ | ✅/⚠️ |
| 凭据治理 | 密码是否 `${ENV_VAR:默认值}` 占位？表是否 `ai_devops_` 前缀 + 审计字段？结构变更是否走 Flyway？ | ✅/⚠️/N/A |
| 文档同步 | tasks 是否含文档同步任务（更新 docs/ + specs/）？ | ✅/⚠️ |

### 维度 5：质量门回顾

- spec 的 `checklists/requirements.md` 校验是否全过？
- plan 的 Constitution Check 表是否逐条填（无跳过）？
- tasks 的 [P] 标记是否合理（同文件冲突误标？）？
- 有无残留 `[NEEDS CLARIFICATION]` 未解决？

## 报告格式

```markdown
## 分析报告：<功能名>

**检查范围**：spec.md / plan.md / tasks.md（列存在的）
**日期**：<今日>

### 摘要
- ✅ 通过项：<数>
- ⚠️ 待修复：<数>
- 严重违规：<数或"无">

### 详细发现

#### 维度 1：spec ↔ plan
- <通过/⚠️> <具体描述，引相关行>

#### 维度 2：plan ↔ tasks
- ...

#### 维度 3：spec ↔ tasks
- ...

#### 维度 4：constitution 合规
- <表格见上>

#### 维度 5：质量门
- ...

### 建议（优先级排序）
1. [P0] <必须修复的违规/严重脱节>
2. [P1] <应修复的对齐问题>
3. [P2] <可选优化>
```

## 行为约束

- **只读不改**：不修改 spec/plan/tasks 任何文件，只产出报告给用户。
- 报告显示给用户后，**不自动执行修复**——由用户决定回哪个命令修（specify/plan/tasks）。
- 无违规时明确告知"全部通过，可继续实现"。

## 下一步建议

- 有 P0/P1 项 → 回对应命令修：spec 漏洞跑 `/speckit-specify`、plan 不全跑 `/speckit-plan`、tasks 不全跑 `/speckit-tasks`。
- 全通过且 tasks 已完成 → `/speckit-implement` 开始实现，或已实现则收尾。
- constitution 有违规 → **必须**回去改 spec/plan/tasks，不许稀释原则。
