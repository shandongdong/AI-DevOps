---
name: speckit-implement
description: Execute the implementation plan by processing all tasks in tasks.md, writing code in the ai-devops module and syncing docs. 读 tasks.md 按 Phase 逐项实现代码 + 更新复选框 + 同步文档。
argument-hint: Optional implementation guidance or task filter (e.g. "only Phase 1" or "T001-T003")
user-invocable: true
disable-model-invocation: false
---

# speckit-implement：按任务清单实现

本命令读 `tasks.md`，按 Phase 顺序逐项实现代码，完成后更新复选框 `[x]` 并同步文档。遵循 spec-kit implement 命令逻辑，适配本项目，**强化 constitution 合规**。无 CLI 依赖。

## User Input

```text
$ARGUMENTS
```

你 **必须** 先考虑用户输入（若非空）——可以是任务过滤（"只做 Phase 1""T001-T003"）或实现指引。若空则从第一个未完成任务开始，按 Phase 顺序。

## 前置检查

1. **定位 feature 目录**：扫 `specs/` 下目录，取待实现的（有 tasks.md）。设 `FEATURE_DIR=specs/<NNN>-<slug>`。
2. **确认 tasks.md 存在**：否则报错"无 tasks.md，先跑 /speckit-tasks"。
3. 读 `FEATURE_DIR/tasks.md`（取任务列表、依赖、[P] 标记）。
4. 读 `FEATURE_DIR/plan.md`（取技术方案、文件落点）。
5. 读 `FEATURE_DIR/spec.md`（取验收标准，实现后对照验证）。
6. 读 `memory/constitution.md` 关键约定（实现时必须遵守）。

## 执行流程

### 1. 扫描任务状态

读 `tasks.md` 所有复选框：
- `[ ]` = 未完成。
- `[x]` = 已完成。
- 从第一个 `[ ]` 任务开始（除非 `$ARGUMENTS` 指定了范围）。

### 2. 按 Phase 顺序逐项实现

严格按 Phase 顺序，遵守依赖：
- **Foundational phase 未完成不得跳到 user story phase**（CRITICAL 约束）。
- 同 phase 内标 `[P]` 的任务可并行/乱序（不同文件无依赖）。
- 每个任务的描述含确切文件路径，按描述改/建文件。

**实现时必须遵守 constitution**（不可妥协）：

- **原则 I 模块隔离**：业务代码落 `ai-devops` 模块，包 `com.ruoyi.aidevops.{controller/service/mapper/domain/util}.{modulexxx}`。**绝不改上游模块**（`ruoyi-admin/framework/system/quartz/generator/common`）——如需复用，依赖 `ruoyi-common` 或引用 `ruoyi-system`。
- **原则 II 白名单**：免登录接口用 `@Anonymous` 注解，**不改 `SecurityConfig`**。
- **原则 III 菜单**：新增页面走 `sys_menu` 表（写 SQL 迁移插记录），**不改前端静态路由**。
- **原则 IV 分层**：Controller → Service → Mapper → Domain 单向，不跨层调用。
- **原则 V 简单性**：直接用 Spring/MyBatis/若依特性，不套额外抽象层。
- **凭据治理**：密码用 `${ENV_VAR:默认值}` 占位（dev 真实默认值 / test-prod `changeme`），**不硬编码明文密码**；业务表 `ai_devops_` 前缀 + 审计字段；结构变更走 Flyway（`db/migration/V{N}__desc.sql`，业务表 V3 起编号）。

### 3. 每个任务完成后

- 更新 `tasks.md` 对应复选框 `[ ]` → `[x]`。
- 对照该任务所属 user story 的验收场景，验证是否达成（能跑则跑验证，如 `curl` / `mvn` 编译）。
- 每个 checkpoint 停下，让用户决定继续还是先验证整个 story。

### 4. 文档同步（constitution 质量门，强制）

实现过程中涉及以下变更时**必须同步更新**（不能等最后批量）：

- 新增/改 ai-devops 模块包结构 → 更新 `docs/development/5.功能模块分析.md` + `docs/development/6.项目目录结构.md`。
- 新增业务表 → 更新 `docs/development/7.数据库ER关系图.md`（如有业务表关系）+ specs 产物。
- 功能落地 → 更新 `specs/<NNN>/` 产物状态（spec.md Status 段改 ✅ Implemented）+ `specs/README.md` 已有功能表加行。
- 配置/部署变化 → 更新 `docs/deploy/` 与 `CLAUDE.md` 相关段。

> 文档不同步 = 质量门未过，视为实现未完成。

### 5. 提交（每逻辑组）

每个任务或逻辑组完成后提交（用户若要求才提交，否则只 stage）：
- commit 格式：`type(scope): 中文描述`，scope 用模块级（`ai-devops`/`admin`/`system`/`ui`/`docs-*` 等）。
- **不加 `Co-Authored-By`**。
- 删除类操作单独成提交（见 `docs/development/8.项目开发规范.md` 冲突处理）。

## 完成报告

报告：已完成任务数/总数、每 phase 进度、文档同步情况、是否所有 user story 验收场景通过。

## 下一步建议

- 实现完成 → **`/speckit-analyze`** 跨产物一致性复查。
- 若新增 user story 或发现 spec/plan 漏洞 → 回 `/speckit-specify` 或 `/speckit-plan` 补全（SDD 双向反馈）。
