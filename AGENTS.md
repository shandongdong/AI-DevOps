# AGENTS.md — Agent 协作指引

本文件是给 AI 编码助手（Claude Code 等）的**工作流入口**，把项目已有资产串成一条"规范驱动开发"链路。详细项目约定见 `CLAUDE.md`，不可妥协的治理基线见 `memory/constitution.md`，SDD 产物约定见 `specs/README.md`。

## 项目一句话

AI-DevOps —— 基于若依（RuoYi-Vue 3.9.2）二次开发 + SDD（Spec-Driven Development）规范驱动。后端 Spring Boot 4.1 / Java 17 多模块，前端 Vue 3 + Vite。自有业务在独立的 `ai-devops` 模块，上游永不触碰。

## 开发流程：SDD 主链路

所有新功能 / 重构 / 非平凡改动**必须走 SDD 流程**，不得直接写代码：

```
/speckit.specify  → specs/NNN-slug/spec.md      （做什么、为什么，不涉技术栈）
/speckit.plan     → specs/NNN-slug/plan.md       （怎么做 + Constitution Check 合规）
/speckit.tasks    → specs/NNN-slug/tasks.md      （拆任务，标 [P] 并行）
/speckit.implement→ 按tasks.md逐项写代码          （落 ai-devops 模块 + 同步文档）
/speckit.analyze   → 只读跨产物一致性检查          （spec/plan/tasks 间是否脱节、是否违宪）
```

SDD 命令已落地为 Claude Code skill，见 `.claude/skills/speckit-*/SKILL.md`。命令入口即 `/speckit.specify` 等。产物落在 `specs/NNN-slug/`（NNN 扫现有目录递增、零填充 3 位），命名约定见 `specs/README.md`。样板见 `specs/001-health-check/`。

## 必读基线

动手前**必须读** `memory/constitution.md`（v1.0.0，治理宪法，凌驾于所有实践之上）。它定义了五条原则与凭据治理：

1. **模块隔离**：业务落 `ai-devops` 模块（包 `com.ruoyi.aidevops.*`），不改上游模块（`ruoyi-system` 等）。
2. **注解化白名单**：免登录用 `@Anonymous` 注解，**不改 `SecurityConfig` 硬编码**。
3. **菜单数据驱动**：菜单/权限走 `sys_menu` 表 + 前端动态路由，**不改前端静态路由**。
4. **分层单向依赖**：Controller → Service → Mapper → Domain，ai-devops 按功能域子包聚合。
5. **YAGNI / 反过度抽象**：单体多模块，不引入未用的抽象层，直接用框架特性。

外加：
- **凭据不入库**：密码用 `${ENV_VAR:默认值}` 占位（dev 内网默认值 / test-prod `changeme`），IP 非凭据可明文。业务表前缀 `ai_devops_`，结构变更走 Flyway。
- **文档同步是质量门**：改代码同步更新 `docs/` 与 `specs/`。
- **提交规范**：`type(scope):` 中文描述，**不加 `Co-Authored-By`**。GitFlow 双 remote（`origin` 私有 / `upstream` 若依只读）。

plan 阶段的 `## Constitution Check` 段必须逐条对照上述原则评估——与 MUST 冲突的，改 spec/plan/tasks 解决，**不是稀释原则**。

## 指引分工（别读错文件）

| 文件 | 角色 | 何时读 |
|---|---|---|
| `AGENTS.md`（本文件）| 工作流入口，SDD 链路与必读基线 | 每次会话起手 |
| `CLAUDE.md` | 项目约定速查（模块/构建/MCP/文档导航）| 需要项目细节时 |
| `memory/constitution.md` | 不可妥协治理基线 | plan 合规、动手前 |
| `specs/README.md` | SDD 产物约定（NNN-slug 命名、文件结构）| 建/读 specs 时 |
| `.claude/skills/speckit-*/SKILL.md` | SDD 命令实现（specify/plan/tasks/implement/analyze）| 执行 SDD 命令时 |
| `docs/` | 项目级静态文档（技术栈/架构/规范/部署）| 深读项目时 |

## 关键约定提醒

- **业务代码落 `ai-devops` 模块**，包 `com.ruoyi.aidevops.{controller/service/mapper/domain/util}.{modulexxx}`。
- **不改上游模块**（`ruoyi-admin/framework/system/quartz/generator/common`），冲突处理见 `docs/development/8.项目开发规范.md`。
- **白名单用 `@Anonymous`，菜单走 `sys_menu`**——两者都不改静态文件。
- **业务表 `ai_devops_` 前缀**，结构变更走 Flyway（`ai-devops/src/main/resources/db/migration/`，业务表 V3 起编号）。
- **commit**：`feat(ai-devops):` / `docs(deploy):` / `chore(pom):` 等模块级 scope，中文描述，无 Co-Authored-By。
