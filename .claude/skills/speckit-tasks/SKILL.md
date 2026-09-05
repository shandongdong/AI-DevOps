---
name: speckit-tasks
description: Generate a dependency-ordered tasks.md from the plan and spec, marking parallel tasks with [P]. 读 plan+spec，写 tasks.md，按 Phase 组织、[P] 标并行。
argument-hint: Optional task generation constraints (e.g. focus on MVP phase only)
user-invocable: true
disable-model-invocation: false
---

# speckit-tasks：拆解任务清单 tasks.md

本命令读 `plan.md` + `spec.md`，生成依赖排序的任务清单 `tasks.md`，按 Phase 组织、独立任务标 `[P]` 可并行。遵循 spec-kit tasks 命令逻辑，适配本项目路径，无 CLI 依赖。

## User Input

```text
$ARGUMENTS
```

你 **必须** 先考虑用户输入（若非空）——通常是任务生成约束，如"只拆 MVP 阶段""按 user story 分组"。若空则覆盖全部 plan。

## 前置检查

1. **定位 feature 目录**：扫 `specs/` 下目录，取待 tasks 的（有 plan.md 但无/未完成 tasks.md）。设 `FEATURE_DIR=specs/<NNN>-<slug>`。
2. **确认 plan 存在**：`FEATURE_DIR/plan.md` 必须存在，否则报错"无 plan.md，先跑 /speckit-plan"。
3. 读 `FEATURE_DIR/spec.md`（取 user story 作任务分组依据）。
4. 读 `FEATURE_DIR/plan.md`（取技术方案、文件落点、Implementation Phases）。
5. 参考 `specs/001-health-check/tasks.md` 格式（样板）。

## 执行流程

### 写 tasks.md（写入 FEATURE_DIR/tasks.md）

按以下结构（对齐 `specs/001-health-check/tasks.md`）：

```markdown
---
description: "Task list for <功能名> feature implementation"
---

# Tasks: <功能名>

**Input**: Design documents from `FEATURE_DIR/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)
**Tests**: <测试策略：JUnit5 哪些 / 手动验证哪些；无自动化测试则说明>
**Organization**: Tasks 按 user story 组织，便于独立实现与测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同 phase 内其他任务并行（不同文件、无依赖）
- **[Story]**: 该任务属于哪个 user story
- 描述中含确切文件路径

## Path Conventions

- 后端模块：`ai-devops/src/main/java/com/ruoyi/aidevops/...`
- 根 pom：`pom.xml`，启动模块 pom：`ruoyi-admin/pom.xml`
- 迁移脚本：`ai-devops/src/main/resources/db/migration/V{N}__<desc>.sql`

---

## Phase 1: Setup (Shared Infrastructure)
**Purpose**: <模块/依赖初始化>

- [ ] T001 [P] [USx] <任务描述，含文件路径>
- [ ] T002 [USx] <串行任务说明依赖 T001>

## Phase 2: Foundational (Blocking Prerequisites)
**Purpose**: <前置依赖，阻塞 user story>
**⚠️ CRITICAL**: 本 phase 完成前不得开始 user story 实现

- [ ] T003 [USx] <前置任务>

**Checkpoint**: <完成标志，如编译成功>

## Phase 3: User Story X - <故事名> (Priority: Px) 🎯 MVP
**Goal**: <此 story 目标>
**Independent Test**: <独立验证方式>

### Implementation for User Story X
- [ ] T00x [P] [USx] <实现任务>
**Checkpoint**: <story 应已可用可独立测试>

## Phase 4: Polish & Cross-Cutting Concerns
**Purpose**: <横切：文档同步、对照验证>
- [ ] [P] [USx] <文档更新：更新 docs/design-docs/功能模块分析.md、docs/ARCHITECTURE.md、docs/README.md>
- [ ] [P] [USx] <对照验证：验证 spec 验收场景>

---

## Dependencies & Execution Order

### Phase Dependencies
- Setup (Phase 1): 无依赖
- Foundational (Phase 2): 依赖 Setup，阻塞所有 story
- User Story (Phase 3): 依赖 Foundational
- Polish (Phase 4): 依赖 story 完成，可交错

### Parallel Opportunities
- <哪些 [P] 任务可并行>

## Implementation Strategy

### MVP First (User Story <X> Only)
1. Phase 1 → Phase 2（CRITICAL）→ Phase 3 (MVP story) → **STOP and VALIDATE**
2. <其余 story 后续迭代>

## Notes

- [P] = 不同文件、无依赖
- [Story] 映射到具体 user story
- 每个 user story 独立可完成可测试
- 每个任务或逻辑组完成后提交（commit `type(scope):` 中文，不加 Co-Authored-By）
- 任何 checkpoint 可停下独立验证 story
- 避免：模糊任务、同文件冲突、破坏独立性的跨 story 依赖
```

### 拆分原则

- **MVP first**：优先把最高优先级 user story 拆完整可测的切片。
- **[P] 标记**：同 phase 内不同文件、无依赖的任务标 `[P]`，可并行执行。
- **依赖显式**：串行依赖在描述里写明（"T002 依赖 T001"）。
- **Checkpoint**：每个 phase 末尾给完成标志（编译成功、接口可验证等）。
- **文档同步任务**：Polish phase 必须含"更新 docs/ + specs/ 产物"任务（constitution 质量门）。
- **每任务含文件路径**：描述里写明要改/建的确切路径，便于 implement 直接执行。

### 完成报告

报告：`tasks.md` 路径、任务总数、Phase 数、MVP story 标识、并行点。

## 下一步建议

tasks 写完后，运行 **`/speckit-implement`** 按 Phase 逐项实现；或先 **`/speckit-analyze`** 查 spec/plan/tasks 三者一致性。
