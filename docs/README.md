# 项目文档

> 本文档体系描述 **AI-DevOps** 项目（基于 RuoYi-Vue 3.9.2 二次开发，Spring Boot 4.1.0 / Java 17 / Vue 3 + TypeScript）的现状与规范。

## 快速导航

| 分类 | 文档 | 说明 |
|------|------|------|
| 快速开始 | [项目快速启动](design-docs/项目快速启动.md) | **新人先读**：从克隆到联调的完整搭建流程 |
| 总览 | [技术方案](design-docs/技术方案.md) | 项目整体技术方案与部署 |
| 架构 | [项目技术栈](design-docs/项目技术栈.md) | Spring Boot 4.1 / Java 17 / Vue 3 等 |
| 架构 | [ARCHITECTURE](ARCHITECTURE.md) | 架构顶层地图：分层 + 模块依赖 + 目录结构 + 请求链路 |
| 架构 | [功能模块分析](design-docs/功能模块分析.md) | 7 个模块功能拆解 |
| 参考 | [数据库 ER 关系图](design-docs/数据库ER关系图.md) | sys_ 表关系 |
| 开发 | [项目开发规范](design-docs/项目开发规范.md) | 分层、命名、注解、Git 约定 |
| 产品规格 | [product-specs/](product-specs/) | 功能模块完整静态文档（模块全貌） |
| 技术债 | [tech-debt-tracker](tech-debt-tracker.md) | 技术债与已否决方案追踪 |

## 子目录

| 目录 | 说明 |
|------|------|
| [design-docs/](design-docs/) | 设计文档（技术方案/架构/规范/流程等，按语义命名） |
| [product-specs/](product-specs/) | 功能模块完整静态文档（模块全貌，独立维护） |
| [deploy/](deploy/) | 多环境部署文档、Nginx 配置、备份脚本（已落地） |
| [hifi-prototypes/](hifi-prototypes/) | 高保真原型模板（通用 CRUD 模板，双击即跑） |
| [tools/](tools/) | 工具脚本与使用说明（规划中） |

## 关联：SDD 规范驱动开发

`docs/` 是项目级**静态**文档（描述项目是什么样）。项目另有 SDD 产物目录，描述**单个功能的演进**：

| 路径 | 说明 |
|------|------|
| [`../memory/constitution.md`](../memory/constitution.md) | 全局宪法（治理原则，plan 阶段合规依据） |
| [`../specs/`](../specs/) | 功能级 spec/plan/tasks 产物，按 `NNN-slug/` 分目录 |
| [`../specs/README.md`](../specs/README.md) | SDD 目录用法与命名约定 |

新功能开发遵循 SDD 流程：spec → plan（对照 constitution 合规）→ tasks → implement。样板见 `../specs/001-health-check/`。

## 文档维护规则

- 每次代码变更同步更新对应文档（版本号、模块名、端口等以源码实测为准）
- 设计文档（技术方案/架构/规范/流程等）放在 `design-docs/` 目录，按语义命名（不带编号前缀）
- 部署脚本与说明文档放在 `deploy/` 目录
- 新增模块时在此索引中补充条目，并同步更新 `design-docs/功能模块分析.md` 与 `ARCHITECTURE.md` 的目录结构段
- 新增功能时在 `specs/` 建 `NNN-slug/` 产物，并在 `specs/README.md` 索引登记
- 版本类信息统一引用根 `pom.xml` 的 `<properties>`，勿在多处写死
