---
name: speckit-scan
description: Scan for documentation/spec drift against actual code across all features and docs. 全量漂移扫描——以代码为真值，查 docs↔code + specs↔code + spec↔plan↔tasks 跨产物漂移，只读报告不改文件。
argument-hint: Optional focus (e.g. "docs" or "specs" or feature NNN)
user-invocable: true
disable-model-invocation: false
---

# speckit-scan：全量漂移扫描（只读）

本命令**只读**扫描整个项目的文档/specs 与实际代码之间的漂移，以**代码为真值源**，产出漂移报告，**不改任何文件**。这是 OpenAI「代码仓库即记录系统」铁律的落地 sensor——doc-gardening 的雏形，定期或怀疑文档过时时跑。

与 `/speckit-analyze` 的分工：

| | /speckit-analyze | /speckit-scan（本命令）|
|---|---|---|
| 范围 | **单 feature** | **全量**（所有 feature + 所有 docs）|
| 查什么 | spec↔plan↔tasks 跨产物 + constitution 合规 | docs↔code 漂移 + specs↔code 漂移 + 全 feature 跨产物 |
| 真值源 | 三份产物互相对照 | **代码是真值**（Controller 注解 / Flyway 文件名 / SQL / find）|
| 何时跑 | 单 feature tasks 完成后、review 时 | 怀疑文档过时、定期 doc-gardening、release 前 |

## User Input

```text
$ARGUMENTS
```

若非空，可以是聚焦域：
- `docs` → 只跑维度 1（docs↔code 漂移）
- `specs` → 只跑维度 2 + 3（specs↔code + 跨产物）
- `<NNN>`（如 `002`）→ 只扫指定 feature 的 specs↔code + 跨产物
- 空 → 全维度扫描

## 前置检查

1. 读 `memory/constitution.md`（文档同步是质量门基线 + 原则 VI API 语义契约）。
2. **建立代码真值源**（见下节，必须先提取再比对）。

## 真值提取（代码是 source of truth）

scan 的核心是先从代码提取"事实清单"，再拿 docs/specs 里的描述逐一比对。用 Bash/grep/Read 提取以下真值（这些命令是建议形态，实际按项目现状调整）：

**A. 数据库真值**（比对 docs/7 ER 图 + docs/6 目录结构里的表名/迁移文件名）：
```bash
# 实际迁移文件名清单
ls ai-devops/src/main/resources/db/migration/V*.sql
# 实际表名清单（-i 忽略大小写，SQL 大小写不固定）
grep -rhiE "create\s+table\s+ai_devops_" ai-devops/src/main/resources/db/migration/*.sql
```

**B. 接口真值**（比对 docs/5 功能模块分析里的路径/方法）：
```bash
# Controller 类级 base path
grep -rnE "@RequestMapping" ai-devops/src/main/java/.../controller/
# 每个方法的 HTTP 方法 + 路径（注意同时抓类级 + 方法级拼 full path）
grep -rnE "@(Get|Post|Put|Delete)Mapping" ai-devops/src/main/java/.../controller/
```

**C. 文件落点真值**（比对 specs/*/plan.md 声明的文件是否落地）：
```bash
# 实际后端文件树
find ai-devops/src -type f -name "*.java" | sort
# 实际前端文件树
find ruoyi-ui-vue3/src -path "*ai-devops*" -type f | sort
```

**D. 跨产物真值**（遍历所有 `specs/*/`，提取每个 feature 的 spec/plan/tasks）。

## 扫描维度

### 维度 1：docs ↔ code 漂移

对 docs/ 下的文档（尤其 `design-docs/功能模块分析.md` 接口表、`ARCHITECTURE.md` 目录结构段、`design-docs/数据库ER关系图.md` 表名/迁移名），逐一比对代码真值：

- docs 里出现的每个 `ai_devops_*` 表名，是否在 Flyway SQL 的 `create table` 里存在？（表名漂移）
- docs 里出现的每个 `V*__*.sql` 迁移文件名，是否在 `db/migration/` 实际存在？（文件名漂移）
- docs 里出现的每个 `/aidevops/*` 接口路径 + HTTP 方法，是否与 Controller 注解一致？（**尤其原则 VI：有副作用的操作 docs 不能写成 GET**——这是最高优先级漂移）
- docs 里出现的每个 ai-devops Java 类名/包路径，是否在 `find` 结果里？

> 典型漂移样本：docs 写表名 `ai_devops_calc_list`，代码实建 `ai_devops_calc_history`；docs 写 `GET /compute/{a}/{b}/{c}`，代码实际是 `@PostMapping("/compute")` + `@RequestBody`（原则 VI HTTP 语义漂移）。

### 维度 2：specs ↔ code 漂移

对每个 `specs/*/`：

- `plan.md` 声明的文件落点（grep 出所有 `*.java` / `*.vue` / `*.ts` / `*.xml` / `*.sql` 路径），是否都已实际创建？（plan 说有 `CalcController.java`，`find` 里有没有？）
- `tasks.md` 标 `[x]` 的任务，其声明的文件是否真的落地？标 `[ ]` 的任务，文件是否真的不存在？（状态↔落地不符）
- `spec.md` Status 标 ✅ Implemented 的，代码是否真的齐了？
- **反向**：代码里有但 plan 没声明的文件（plan 漏设计或后来手加的）。

### 维度 3：spec ↔ plan ↔ tasks 跨产物（全 feature）

遍历所有 `specs/*/`，对每个 feature 检查（复用 analyze 的维度 1/2/3 逻辑，但**全量浅查**）：

- spec↔plan：plan 是否覆盖 spec 的所有 FR？plan 文件落点是否满足 spec Key Entities？
- plan↔tasks：tasks 是否覆盖 plan 的所有 Phase？文件路径是否一致？
- spec↔tasks：spec 的 user story 是否在 tasks 有对应任务组？

> 不重复 /speckit-analyze 的 constitution 合规维度 4 和质量门维度 5——那是 analyze 的职责，scan 聚焦漂移，YAGNI。

## 报告格式

```markdown
## 漂移扫描报告

**扫描范围**：docs/design-docs/*.md + docs/ARCHITECTURE.md + specs/*/ + ai-devops 实际代码
**日期**：<今日>

### 摘要
- 扫描维度：docs↔code / specs↔code / 跨产物全量
- ✅ 一致：<数>
- ⚠️ 漂移：<数>
- 严重漂移（接口语义/表名）：<数或"无">

### 详细发现

#### 维度 1：docs ↔ code
- ⚠️ [P0] docs/5:67 写 `GET .../compute/{a}/{b}/{c}`，代码实际 `@PostMapping("/compute")` + `@RequestBody`
  （原则 VI HTTP 语义漂移，compute 有副作用不能用 GET）
- ⚠️ [P1] docs/7:3,105,123 写表名 `ai_devops_calc_list`，代码 V3 SQL 实建 `ai_devops_calc_history`
- ⚠️ [P1] docs/6:168, docs/7:3 写迁移文件 `V3__create_ai_devops_calc_list.sql`，实际 `V3__create_ai_devops_calc_history.sql`
- ✅ docs/5:66 HealthController `GET /aidevops/health` 与代码一致

#### 维度 2：specs ↔ code
- ✅ 002-calculator plan 声明的 8 个 Java 文件全落地
- ✅ 001-health-check plan 声明的 HealthController 已落地

#### 维度 3：spec↔plan↔tasks 跨产物（全 feature）
- ✅ 001-health-check：spec↔plan↔tasks 一致
- ✅ 002-calculator：spec↔plan↔tasks 一致

### 建议回修路径（优先级排序）
1. [P0] docs/5:67 HTTP 方法漂移 → compute 已改 POST，docs 没跟上，手动改 docs
2. [P1] docs/6+7 表名/文件名漂移 → calc_list → calc_history，手动改 docs
3. [P2] ...
```

## 行为约束

- **只读不改**：不修改 docs/specs/代码任何文件，只产出报告给用户。
- **不阻断**：漂移可重跑修正（回 specify/plan/tasks/implement 改），不是不可逆违规，不卡开发流程。对应 OpenAI 铁律 3「纠错成本低、等待成本高」。
- **不自动修复**：报告显示给用户后，由用户决定回哪步修。
- **不重复 analyze 的 constitution 合规维度**：scan 聚焦漂移，constitution 合规归 /speckit-analyze，YAGNI。

## 下一步建议

- 有 P0/P1 漂移 → 指明回哪步修：
  - docs 漂移 → 手动改 docs/development 对应文件
  - specs 漂移（plan 声明文件没落地）→ 回 `/speckit-implement` 补实现
  - 代码有但 plan 漏的文件 → 回 `/speckit-plan` 补设计
- 全通过 → "文档与代码一致，无漂移"。
- 建议定期跑（doc-gardening 雏形）：release 前、或怀疑文档过时时。
