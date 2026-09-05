---
description: "Task list for 健康检查接口 feature implementation"
---

# Tasks: 健康检查接口

**Input**: Design documents from `/specs/001-health-check/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: 本功能无自动化测试，仅手动浏览器验证与编译验证。Tests 段从略（spec 未要求自动化测试）。

**Organization**: Tasks 按 user story 组织，便于独立实现与测试。本功能仅 1 个 user story（US1）。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同 phase 内其他任务并行（不同文件、无依赖）
- **[Story]**: 该任务属于哪个 user story（US1）
- 描述中含确切文件路径

## Path Conventions

- 本项目为 Maven 多模块 web 服务，路径基于模块根：
  - 后端模块：`ai-devops/src/main/java/com/ruoyi/aidevops/...`
  - 根 pom：`pom.xml`，启动模块 pom：`ruoyi-admin/pom.xml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 模块初始化与基本结构

- [x] T001 [P] [US1] 新增 `ai-devops/pom.xml`：parent 指向 `com.ruoyi:ruoyi:3.9.2`，依赖 `ruoyi-common`（不写 version，继承 dependencyManagement）
- [x] T002 [US1] 修改根 `pom.xml`：`<modules>` 末尾加 `<module>ai-devops</module>`，`<dependencyManagement>` 声明 `com.ruoyi:ai-devops:${ruoyi.version}`（T002 依赖 T001，模块需先存在）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 启动模块对 ai-devops 的依赖接入，必须在任何 user story 实现前完成

**⚠️ CRITICAL**: 本 phase 完成前不得开始 user story 实现

- [x] T003 [US1] 修改 `ruoyi-admin/pom.xml`：`<dependencies>` 引入 `ai-devops`（不写 version，由 T002 的 dependencyManagement 提供；T003 依赖 T002）

**Checkpoint**: 三个 pom 改完后，`mvn -pl ai-devops -am -DskipTests clean install` 编译成功，ai-devops 装入本地仓库——user story 实现现在可以开始。

---

## Phase 3: User Story 1 - 开发者验证模块就绪 (Priority: P1) 🎯 MVP

**Goal**: 提供免登录的健康检查接口，验证 ai-devops 模块被正确加载

**Independent Test**: 浏览器访问 `http://localhost:8080/aidevops/health` 返回 200 与标准 `AjaxResult`，无需登录

### Implementation for User Story 1

- [x] T004 [P] [US1] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/controller/HealthController.java`：`@RestController` + `@RequestMapping("/aidevops")`，方法 `health()` 标 `@Anonymous` + `@GetMapping("/health")`，返回 `AjaxResult.success(data)`，data 含 `module`/`status`/`timestamp`（`System.currentTimeMillis()`）

**Checkpoint**: 此时 User Story 1 应已完全可用、可独立测试——启动 `RuoYiApplication`，浏览器访问 `/aidevops/health` 返回 200。

---

## Phase 4: Polish & Cross-Cutting Concerns

**Purpose**: 跨多个 user story 的横切改进

- [x] T005 [P] [US1] 编译验证：`mvn -pl ruoyi-admin -am -DskipTests clean package` → `BUILD SUCCESS`，确认 ai-devops 被 admin 正确解析打包
- [x] T006 [US1] 对照验证：访问未加 `@Anonymous` 的接口（如 `/system/user/list`）应被拦截，确认白名单精确生效（spec 场景 2）
- [x] T007 [P] [US1] 文档更新：更新 `docs/design-docs/功能模块分析.md`（补 ai-devops 行）、`docs/ARCHITECTURE.md`（补 ai-devops 树）、`docs/README.md` 索引

**Checkpoint**: 全链路编译通过 + 接口验证通过 + 文档已同步。

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup 完成——阻塞所有 user story
- **User Story 1 (Phase 3)**: 依赖 Foundational 完成
- **Polish (Phase 4)**: 依赖 user story 完成的部分；可交错进行

### Within User Story 1

- 模型/工具类先于服务（本功能无 Service/Mapper，仅 Controller）
- Controller 实现先于验证

### Parallel Opportunities

- Setup 阶段 T001 可与后续无文件重叠的任务并行（本功能 T002 依赖 T001，串行）
- Polish 阶段 T005/T006/T007 互不冲突，可并行

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1: Setup
2. 完成 Phase 2: Foundational（CRITICAL，阻塞所有 story）
3. 完成 Phase 3: User Story 1
4. **STOP and VALIDATE**: 独立测试 User Story 1（浏览器访问 /aidevops/health）
5. 部署/演示（如就绪）

> 本功能仅 1 个 user story，MVP 即完整功能。Polish (Phase 4) 为质量与文档收尾。

---

## Notes

- [P] 任务 = 不同文件、无依赖
- [Story] 标签把任务映射到具体 user story 便于追溯
- 每个 user story 应独立可完成、可测试
- 每个 task 或逻辑组完成后提交（commit 中文 + 前缀，不加 Co-Authored-By）
- 在任何 checkpoint 可停下来独立验证 story
- 避免：模糊任务、同文件冲突、破坏独立性的跨 story 依赖
