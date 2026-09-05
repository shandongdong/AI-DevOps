<!--
SYNC IMPACT REPORT
==================
Initial ratification (MAJOR baseline).

本宪法为 AI-DevOps 项目治理基线，约束所有变更（新功能/新模块/配置改动/文档同步）。
原则提炼自项目既有开发约定（docs/design-docs/项目开发规范.md）与 SDD 九条 article 精神。

Principles defined:
  I.   模块隔离与上游零冲突 (对应 SDD Article IV 项目自定义: 隔离策略)
  II.  注解化白名单 (不改 SecurityConfig)
  III. 菜单数据驱动 (不改前端静态路由)
  IV.  分层单向依赖 (Controller→Service→Mapper→Domain, ai-devops 按功能域子包聚合)
  V.   单体多模块与简单性 (YAGNI, 对应 SDD Article VII/VIII 简单性与反过度抽象)
  VI.  API 语义契约 (HTTP 方法对齐操作语义, 有副作用禁用 GET)

Added sections:
  - Security & 凭据约束 (含业务表前缀 ai_devops_ + Flyway 迁移约定)
  - Development Workflow & Quality Gates (GitFlow + commit 规范 + 文档同步)
  - Governance

Templates reviewed for alignment:
  ✅ spec-kit/templates/plan-template.md — "Constitution Check" gate 由本文件 Principles I–VI 在 plan 时填充
  ✅ spec-kit/templates/spec-template.md — 无 constitution 专属 token, 无需改
  ✅ spec-kit/templates/tasks-template.md — task categories 已容纳本文件要求, 无需改

Follow-up TODOs: none. RATIFICATION_DATE set to first adoption date below.

v1.1.0 (2026-09-03) — MINOR: 新增 Principle VI「API 语义契约」
  Reason: 002-calculator plan review 暴露规范缺口——compute 接口用 GET 落库（有副作用非幂等），
    却因宪法无 HTTP 方法语义条款而 Constitution Check 全过。补原则 VI 强制 plan 阶段审查
    方法语义对齐，堵住"GET 做写操作"类设计缺陷。
  Propagated to:
    - docs/design-docs/项目开发规范.md 1.2 (补 HTTP 方法语义操作细则表)
    - .claude/skills/speckit-plan/SKILL.md (Constitution Check 表 I–V → I–VI)
    - .claude/skills/speckit-analyze/SKILL.md (维度4 表五原则 → 六原则)
    - AGENTS.md (必读基线五条 → 六条)
    - specs/001-health-check/plan.md, specs/002-calculator/plan.md (Check 表补 VI 行)

2026-09-05 — 注释性更新（不 bump 版本，原则内容未变）：
  Principle IV「分层单向依赖」补 Mechanical enforcement 段——引入 ArchUnit 测试
  LayerDependencyTest 在 mvn test 时机械强制四层单向 + Controller 不得直查 Mapper。
  原则 IV 本身未改（仍为分层单向），仅补"由测试机械强制"的执行手段说明。
  Propagated to:
    - docs/design-docs/项目开发规范.md 1.1 (补 ArchUnit 强制说明)
    - AGENTS.md (必读基线原则 4 补 ArchUnit 标注)

2026-09-05 — 注释性更新（不 bump 版本，原则内容未变）：
  Principle I「模块隔离与上游零冲突」补 Mechanical enforcement 段——引入 PreToolUse
  hook 在 .claude/settings.json 机械阻断 Write/Edit 写入 6 个上游模块路径（exit 2）。
  原则 I 本身未改（仍为模块隔离），仅补"由 hook 机械阻断"的执行手段说明。
  这是本项目第二个机械强制条款（继原则 IV ArchUnit 之后）：
  原则 IV = Computational sensor（测试期拦层内依赖），原则 I = 阻断 sensor（工具期拦跨模块写入）。
  Propagated to:
    - .claude/settings.json (新增 PreToolUse 段，12 条 if)
    - docs/design-docs/项目开发规范.md (补 hook 阻断说明)
    - AGENTS.md (必读基线原则 1 补 hook 标注)
-->

# AI-DevOps Constitution

AI-DevOps（基于若依 RuoYi-Vue 3.9.2 二次开发，Spring Boot 4.1.0 / Java 17 多模块 + Vue 3 前端）是一个持续同步上游更新、独立开发自有业务 `ai-devops` 模块的项目。这些原则提炼自项目既有开发约定（`docs/design-docs/项目开发规范.md`）与 SDD 九条 article 精神，对所有变更具有约束力——包括新功能、新模块、配置改动与文档同步。

## Core Principles

### I. 模块隔离与上游零冲突

自有业务必须放在独立的 `ai-devops` Maven 模块（包 `com.ruoyi.aidevops.*`），不得在 `ruoyi-system` 等上游模块里加业务代码。上游模块来自若依官方，`git merge upstream/master` 时会冲突；`ai-devops/` 目录上游永不触碰，故零冲突。

- **新建功能落 `ai-devops` 模块**，包名在 `com.ruoyi.*` 下（启动类 `com.ruoyi.RuoYiApplication` 默认扫描范围，无需改启动类）。
- **不得改上游模块源码**实现自有业务；如需复用，通过依赖 `ruoyi-common` 或引用 `ruoyi-system`。
- **不得为单一功能新建额外模块或引入微服务**，除非有明确的复杂度与团队规模需求。

**Rationale:** 持续同步上游是本项目的前提，模块隔离把"冲突救火"变成"集中在根 pom 等少量可预测文件"。这与 SDD Article VII（简单性）一致——最小结构、拒绝过度拆分。

**Mechanical enforcement:** 本原则由 `.claude/settings.json` 的 PreToolUse hook 机械阻断——Write/Edit 工具试图写入 `ruoyi-admin/framework/system/quartz/generator/common` 任一上游模块路径时，hook `exit 2` 直接拦截，工具调用失败、文件不写入，stderr 返回原则 I 阻断理由。AI 无法绕过（阻断发生在工具执行前）。这是 constitution 原则中第二个从"文字约束"升级为"机器可查 sensor"的条款（继原则 IV 的 ArchUnit 之后），与原则 IV 互补：原则 IV 管层内单向（Computational sensor，测试期拦），原则 I 管模块隔离（阻断 Sensor，工具期拦）。

### II. 注解化白名单

接口免登录（白名单）一律用 `@Anonymous` 注解（`ruoyi-common.annotation.Anonymous`），由 `PermitAllUrlProperties`（`ruoyi-framework`）启动时自动扫描放行。不得改 `SecurityConfig` 硬编码加白名单。

**Rationale:** `SecurityConfig` 是上游文件，硬编码改动会在 merge upstream 时冲突；注解方式把白名单逻辑写在自有 Controller 内，天然符合原则 I（模块隔离），且是若依推荐做法。

### III. 菜单数据驱动

菜单与权限走数据库 `sys_menu` 表，前端动态路由。新增页面写 Vue 组件 + 在 `sys_menu` 插记录（`component` 字段指向前端组件路径），不得改前端静态路由文件。

**Rationale:** 若依菜单是数据库驱动的，改静态路由既会与上游前端冲突，又绕过了权限体系。数据库记录可随 SQL 迁移、可被运维管理，是唯一一致的做法。

### IV. 分层单向依赖

严格 Controller → Service → Mapper → Domain 四层，依赖方向单向向下。Controller 不写业务逻辑、不直接调 Mapper；Service 不直接返回前端对象；Mapper 不写业务判断。

- **ai-devops 按功能域分包**：`com.ruoyi.aidevops.{controller/service/mapper/domain/util}.{modulexxx}`，每个功能域自带完整四层，便于按域增删与 LLM 生成。

**Rationale:** 分层是若依的既有架构，单向依赖保证可测试性与可替换性。跨层调用（如 Controller 直查 Mapper）会绕过事务边界与日志切面，是 bug 与安全漏洞的温床。功能域子包让单个业务域各层文件聚拢，优于"按层堆叠"的扁平结构。

**Mechanical enforcement:** 本原则由 `ai-devops` 模块的 ArchUnit 测试 `com.ruoyi.aidevops.architecture.LayerDependencyTest` 在 `mvn test` 时机械强制——四层单向依赖 + Controller 不得直查 Mapper，违规代码测试期即失败。这是 Constitution 原则中第一个从"文字约束"升级为"机器可查 Computational sensor"的条款。

### V. 单体多模块与简单性（YAGNI）

沿用若依单体 + Maven 多模块结构（7 个模块：admin/framework/system/quartz/generator/common + ai-devops）。不引入额外的抽象层、仓储模式包装、或未使用的扩展点。直接使用框架特性，而非包装它。

- **最多 3 个项目层**（本项目为单体，无此压力，但禁止无端拆分）。
- **直接用 Spring/MyBatis/若依特性**，不套额外框架。
- **无未来证明式抽象**——YAGNI。

**Rationale:** 对应 SDD Article VII（简单性）与 Article VIII（反过度抽象）。LLM 生成代码时倾向过度抽象，本条强制在 plan 的 Constitution Check 里证明每个抽象的必要性。

### VI. API 语义契约

HTTP 方法必须对齐操作语义，不得为拼路径便利而错配方法：

- **GET = 查询**（幂等、安全、无副作用）——只读，不改服务器状态。
- **POST = 创建 / 触发动作**（非幂等、有副作用）——落库、改状态、触发副作用。
- **PUT = 整体更新**（幂等）——全量替换资源。
- **DELETE = 删除**——删除资源（本项目软删除，仍用 DELETE）。
- **有副作用的操作（落库、改状态、触发副作用）一律禁用 GET**——GET 期望安全幂等，错配会被浏览器预取、CDN 缓存、日志重放误触发，产生脏数据。

**Rationale:** RESTful 方法语义是 API 设计的基本约束，错配（如用 GET 做写操作）会引发难以排查的脏数据与安全问题。本项目 002-calculator 的 compute 接口曾用 GET 落库历史记录，因宪法无方法语义条款而 Constitution Check 全过——此原则堵住该缺口。详细操作映射表见 `docs/design-docs/项目开发规范.md` 1.2。

## Security & 凭据约束

- **凭据不入库**：数据源、Redis 等连接信息含真实密码时，禁止明文入库。采用 `${ENV_VAR:默认值}` 占位：**dev** 默认值用内网开发库密码（本地友好），**test/prod** 默认值用 `changeme` 占位符（库内零真实凭据，部署前必须 export 环境变量，未设则连接失败）。IP 非凭据，可保留明文。本地可用 `application-local.yml`（加 `.gitignore`）覆盖。
- **明文密码进 git 历史无法彻底删除**（需改写历史），发生泄露应换密码 + 后续用环境变量管理。
- **业务表前缀隔离**：ai-devops 业务表用 `ai_devops_` 前缀，与 `sys_`/`gen_` 区分；必备审计字段 `create_by`/`create_time`/`update_by`/`update_time`/`del_flag`。
- **结构变更走 Flyway**：**所有**表结构变更（含若依基础表 sys_*/gen_* 与 ai-devops 业务表 ai_devops_*）统一通过 Flyway 迁移管理，脚本放 `ai-devops/src/main/resources/db/migration/`，命名 `V{N}__desc.sql`。V1/V2 已纳入若依基础表（ry_20260417.sql）与 quartz 表（quartz.sql）；ai-devops 业务表从 V3 起编号。因启用 Flyway 前这些基础脚本已手动执行过，`FlywayConfig` 设 `baselineVersion("2")`：已有库打 v2 基线跳过 V1/V2（不重跑不覆盖数据），空库则执行初始化。Flyway 为唯一执行入口，不再保留独立 sql/ 目录。因若依动态数据源排除了 `DataSourceAutoConfiguration`，Flyway 须手动绑定主数据源（详见 `docs/design-docs/项目开发规范.md` 3.2）。配置类已落地于 `ai-devops/src/main/java/com/ruoyi/aidevops/config/FlywayConfig.java`，开关 `spring.flyway.enabled`（默认关，生产/测试按 profile 开启）。

## Development Workflow & Quality Gates

- **分支命名**遵循 GitFlow：`master`（基线/生产）、`develop`（开发集成）、`feature/<number>-<slug>`、`release/*`、`hotfix/*`。双 remote 协作：`origin` = 私有仓库，`upstream` = 若依官方（只读同步）。
- **提交信息规范**：前缀 `feat:`/`fix:`/`docs:`/`chore:`/`refactor:` + 中文描述；**不加 `Co-Authored-By` 签名行**。
- **文档同步是质量门**：代码变更同步更新 `docs/`（项目级静态文档）与 `specs/`（功能级 SDD 产物）；版本号统一引用根 `pom.xml` 的 `<properties>`，不得多处写死。
- **新功能走 SDD 流程**：先写 `specs/NNN-slug/spec.md` → `plan.md`（对照本宪法做 Constitution Check）→ `tasks.md` → 再写代码。

## Governance

本宪法凌驾于所有其他开发实践之上；冲突时以本文件为准。既有代码库中它所编纂的模式仍是权威参考。

- **Authority**：Principles I–VI 是约束性门禁。plan 模板的 `## Constitution Check` 段必须对照这些原则评估；与 MUST 冲突的，靠改 spec/plan/tasks 解决，而非稀释原则。
- **Amendments**：修订本文件需 PR + 修订原因 + 维护者批准 + 按下述版本策略 bump，并在顶部 SYNC IMPACT REPORT 记录。任何修订必须同 PR 内传播到依赖的模板与命令指引。
- **Versioning policy（治理用 SemVer）**：MAJOR = 不向后兼容的治理变更或原则删除/重定义；MINOR = 新原则/段或实质性扩展；PATCH = 澄清与无语义改动的精炼。
- **Compliance review**：每个 PR 与评审必须验证对本宪法的合规性。新增复杂度或任何偏离必须在 PR 内（plan 则在 Complexity Tracking 段）给出理由，未说明的违规阻塞合并。

**Version**: 1.1.0 | **Ratified**: 2026-08-20 | **Last Revised**: 2026-09-03 (v1.1.0 +Principle VI)
