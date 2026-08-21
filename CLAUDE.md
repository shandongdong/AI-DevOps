# CLAUDE.md

## 项目概况

AI-DevOps — 基于若依（RuoYi-Vue）3.9.2 二次开发的前后端项目，后端 Spring Boot 4.1.0 / Java 17 多模块，前端 Vue 3 + TypeScript + Vite。自有业务在独立的 `ai-devops` 模块（上游永不触碰，零冲突）。

- **后端**：Maven 多模块（7 个：ruoyi-admin/framework/system/quartz/generator/common + ai-devops）
- **前端**：`ruoyi-ui-vue3`（Vue3 + Element Plus + Vite + Pinia），zip 引入，非 git submodule
- **远程模型**：`origin` = 私有仓库 AI-DevOps，`upstream` = 若依官方 yangzongzhuan/RuoYi-Vue（只读同步）
- **分支**：GitFlow（master / develop / feature/\* / release/\* / hotfix/\*）

## 基本原则

- **以** **`docs/`** **目录文档为准**：当官方文档与项目文档冲突时，以项目文档为准
- **文档同步**：代码变更后同步更新 `docs/` 下相关文档
- **不编造信息**：无法确信时主动声明"我不知道"
- **方案先行**：有多个实现方案时，先比较优劣，征求用户意见后再实施

## 文档导航

> AI 需要详细信息时，主动读取对应文件。版本号/模块名/端口等以源码实测为准。

| 分类     | 路径                        | 说明                                    |
| ------ | ------------------------- | ------------------------------------- |
| 文档索引   | `docs/README.md`          | 文档总入口与导航                              |
| 快速启动   | `docs/development/1.项目快速启动.md`        | **新人先读**：从克隆到联调的完整搭建流程                |
| 技术方案   | `docs/development/2.技术方案.md`          | 整体技术方案与部署                             |
| 技术栈    | `docs/development/3.项目技术栈.md`         | Spring Boot 4.1 / Java 17 / Vue 3 等版本 |
| 架构图    | `docs/development/4.项目架构图.md`         | 前后端分层 + Maven 模块依赖                    |
| 功能模块   | `docs/development/5.功能模块分析.md`        | 7 模块功能拆解                              |
| 目录结构   | `docs/development/6.项目目录结构.md`        | 完整模块与包路径树                             |
| 数据库 ER | `docs/development/7.数据库ER关系图.md`      | sys\_ 表关系                             |
| 开发规范   | `docs/development/8.项目开发规范.md`        | 分层、命名、注解、Git 约定                       |
| SDD 规范 | `memory/constitution.md`  | 全局宪法（治理原则，plan 合规依据）                  |
| 功能规范   | `specs/NNN-slug/`         | 每个功能的 spec/plan/tasks 产物（SDD）         |
| SDD 索引 | `specs/README.md`         | 规范驱动开发目录用法与命名约定                       |
| 部署     | `docs/deploy/`            | 多环境部署文档、Nginx 配置、备份脚本（已落地）      |
| 工具     | `docs/tools/`             | 工具脚本说明（规划中）                           |

## 关键约定

- **新建业务放** **`ai-devops`** **模块**（包 `com.ruoyi.aidevops.*`），落在启动类 `com.ruoyi.RuoYiApplication` 默认扫描范围内，**不改上游模块**。详见 `docs/development/8.项目开发规范.md` 1.7。
- **新功能走 SDD 流程**：先写 `specs/NNN-slug/spec.md`（做什么）→ `plan.md`（怎么做，对照 `memory/constitution.md` 合规）→ `tasks.md`（拆任务标 `[P]`）→ 再写代码。样板见 `specs/001-health-check/`。详见 `specs/README.md`。**SDD 命令已落地为 Claude Code skill**（见 `.claude/skills/speckit-*/SKILL.md`），用 `/speckit.specify` 起步，Agent 协作入口见 `AGENTS.md`。
- **ai-devops 业务表前缀** `ai_devops_`，与 `sys_`/`gen_` 区分。
- **冲突处理**：你的独立模块文件保留你的；上游文件优先保留上游；配置类（yml/pom）手动合并保留双方。详见 `docs/development/8.项目开发规范.md` 四。

## 可用 MCP 工具

| 服务         | 用途                              | 使用场景                              |
| ---------- | ------------------------------- | --------------------------------- |
| MySQL      | 直接查询开发环境数据库（192.168.30.41）     | 排查数据问题、验证 SQL、查看表结构               |
| Redis      | 查询/操作开发环境缓存（192.168.30.41:6379）| 查在线用户、排查缓存问题、清理缓存                 |
| Playwright | 浏览器自动化                          | UI 测试、截图验证、前端功能验证                 |

可使用 MCP 或 CLI 命令（如 `redis-cli`、`mysql` 客户端、`git` 远程操作），MCP 工具已配置好连接信息，无需手动传参。CLI 命令如需要账号密码可以从 MCP 配置文件中获取。

## 构建与启动

```bash
# 后端构建
mvn clean package -DskipTests
# 后端启动（IDE 运行 com.ruoyi.RuoYiApplication，或 java -jar ruoyi-admin/target/ruoyi-admin.jar）
# 后端默认端口 8080，context-path /

# 前端开发（端口 80，代理 → localhost:8080）
cd ruoyi-ui-vue3
corepack enable && corepack prepare yarn@1.22.22 --activate
yarn --registry=https://registry.npmmirror.com
yarn dev

# 验证 ai-devops 模块（无需登录）
curl http://localhost:8080/aidevops/health
```
