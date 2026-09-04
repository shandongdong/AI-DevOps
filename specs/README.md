# Specs — 规范驱动开发（SDD）

> 本目录遵循 [Spec-Driven Development](https://github.com/github/spec-kit) 实践：每个功能先写规范，再由规范驱动出计划、任务，最后才写代码。与 `docs/`（项目级静态文档）互补——这里记录**单个功能的演进产物**。

## 目录结构

```
memory/
└── constitution.md        # 全局宪法（治理原则，所有 plan 对照它做合规）

specs/
├── README.md              # 本文件
└── NNN-slug/              # 一个功能一个目录
    ├── spec.md            # 需求、用户故事、验收标准（做什么、为什么）
    ├── plan.md           # 技术方案 + Constitution Check（怎么做）
    ├── tasks.md          # 任务清单，[P] 标并行项
    ├── research.md       # 调研笔记（可选，plan 阶段产出）
    ├── data-model.md     # 数据模型（可选，plan 阶段产出）
    ├── contracts/        # API 契约（可选，plan 阶段产出）
    ├── quickstart.md     # 验证场景（可选，plan 阶段产出）
    └── implementation-details/  # 细节算法/代码片段（可选）
```

> 说明：`research.md` / `data-model.md` / `contracts/` / `quickstart.md` 是 plan 阶段按需产出的可选文件，简单功能（如本目录的 001 健康检查）不需要全部生成。是否使用 `specify` CLI 不影响这些路径约定——CLI 只是自动填充，手写同样遵循此结构。

## 命名约定

| 项 | 约定 | 示例 |
|------|------|------|
| 功能目录 | `NNN-slug`，NNN 自增零填充（超 3 位自动扩展） | `001-health-check`、`003-chat-system`、`1000-xxx` |
| 分支名 | 语义化，通常与功能目录同名 | `001-health-check` |
| 文件名 | 固定标准名，不可改 | `spec.md` / `plan.md` / `tasks.md` 等 |

## 工作流

```
constitution（一次，长期不变，位于 memory/）
      │
      ▼
    spec          /描述 what & why，不涉技术栈
      │
      ▼
   plan           /翻译成架构 + 对照 constitution 合规（Constitution Check）
      │
      ▼
  tasks           /拆任务，标 [P] 并行
      │
      ▼
implement         /按 tasks.md 逐项实现
```

## 已有功能

| 编号 | 功能 | 状态 |
|------|------|------|
| 001 | [健康检查接口](001-health-check/spec.md) | ✅ 已落地 |
| 002 | [计算器](002-calculator/spec.md) | ✅ 已落地 |

## 维护规则

- 新功能先建 `specs/NNN-slug/spec.md`，再动代码（brownfield 已做完的也可追溯建档）。
- 功能变更同步更新对应 `specs/NNN-slug/` 产物。
- constitution 修订需记录原因并给迁移计划（见 `memory/constitution.md` Governance 段）。
- 不装 `specify` CLI 也可手写本目录文件，参照 `001-health-check/` 为样板。
- **命令入口**：SDD 流程已落地为 Claude Code skill（`.claude/skills/speckit-*/SKILL.md`），用 `/speckit-specify` → `/speckit-plan` → `/speckit-tasks` → `/speckit-implement` 驱动；`/speckit-analyze` 做只读一致性检查。Agent 协作指引见根 `AGENTS.md`。
- 模板源参考：[github/spec-kit](https://github.com/github/spec-kit) 的 `templates/` 目录。
