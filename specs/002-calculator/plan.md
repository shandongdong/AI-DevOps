# Implementation Plan: 计算器

**Branch**: `002-calculator` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-calculator/spec.md`

## Summary

在 `ai-devops` 模块内新建计算器功能：后端按功能域子包 `com.ruoyi.aidevops.{controller,service,mapper,domain}.{calc}` 实现四则运算 + 历史记录 CRUD，运算结果落库 `ai_devops_calc_history`（软删除）；表结构与菜单由 Flyway `V3` 迁移统一创建；接口走若依标准 `@PreAuthorize("@ss.hasPermi('aidevops:calc:*')")` 权限控制；前端用 Vue3 `<script setup lang="ts">` + Element Plus 重写按钮式计算器页与历史列表页，走动态路由 + `sys_menu` 菜单。覆盖 constitution 六原则 + 凭据治理 + Flyway + 软删除 + Long 精度（主键 `calc_id` 为 Long，加 `@JsonSerialize`）。

## Technical Context

- **Language/Version**: Java 17 / Spring Boot 4.1.0
- **Primary Dependencies**: `ruoyi-common`（提供 `AjaxResult` / `TableDataInfo` / `BaseController` / `BaseEntity` / `@PreAuthorize` / `@Log` / `BusinessType` / `SecurityUtils`）；`ruoyi-system`（MyBatis 配置、分页 PageHelper 由 ruoyi-framework 提供）。无新增第三方依赖。
- **Storage**: MySQL，表 `ai_devops_calc_history`（`ai_devops_` 前缀 + 审计字段 + `del_flag` 软删除）。结构变更走 Flyway。
- **Testing**: JUnit5 + Mockito，覆盖运算逻辑（含除零）与 Service 历史查询/软删除；无集成测试。测试依赖在 `ai-devops/pom.xml` 显式声明 `spring-boot-starter-test`（test scope，版本走 Spring Boot BOM，ruoyi-common 不传递测试依赖）。
- **Target Platform**: Linux/macOS，内嵌 Tomcat 8080
- **Project Type**: web-service（Maven 多模块）
- **Constraints**: 登录鉴权 + 权限按钮 + 菜单走 sys_menu + 不改上游模块 + 不改 SecurityConfig + 软删除 + 文档同步

## Design

### 数据模型

表 `ai_devops_calc_history`：

```sql
create table ai_devops_calc_history (
  calc_id        bigint(20)   not null auto_increment    comment '记录ID',
  first_number   double(16,4)                            comment '第一个数',
  second_number  double(16,4)                            comment '第二个数',
  operator       char(1)                                 comment '运算符(+ - * /)',
  result         double(16,4)                            comment '运算结果',
  status         char(1)       default '0'               comment '状态（0正常 1停用）',
  del_flag       char(1)       default '0'               comment '删除标志（0存在 2删除）',
  create_by      varchar(64)   default ''                comment '创建者',
  create_time    datetime                                comment '创建时间',
  update_by      varchar(64)   default ''                comment '更新者',
  update_time    datetime                                comment '更新时间',
  remark         varchar(500) default null              comment '备注',
  primary key (calc_id)
) engine=innodb auto_increment=1 comment='计算器历史记录表';
```

> **运算符统一方案**：UI 显示 `×` `÷`，库里统一存 `+` `-` `*` `/` 四则标准符号（`char(1)`），查询过滤按此符号匹配。spec Key Entities 写的"x/÷"在 plan 明确为此方案。

### API 契约

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/aidevops/calc/compute` | `aidevops:calc:compute` | 四则运算 + 落库，JSON body `{operator, first, second}`，返回 `AjaxResult`（含 result） |
| GET | `/aidevops/calc/list` | `aidevops:calc:list` | 历史列表（分页，按 operator/createTime 过滤，按当前用户隔离） |
| DELETE | `/aidevops/calc/{calcIds}` | `aidevops:calc:remove` | 软删除（支持单/批量） |

> 计算接口用 POST + JSON body（RESTful：运算会创建历史记录，非幂等、有副作用，不应用 GET）；入参用 Java 17 record `CalcComputeRequest(operator, first, second)` 承载，对齐若依 add/edit 的 `@RequestBody` 惯例。除零返回 `AjaxResult.error("除数不能为0")`，不落库。

### 文件落点

后端（落 `ai-devops` 模块，按功能域 `calc` 子包，遵循 constitution 原则 I + IV）：

```
ai-devops/src/main/java/com/ruoyi/aidevops/
├── controller/calc/CalcController.java          # @RestController + @PreAuthorize + @Log
├── service/calc/
│   ├── ICalcHistoryService.java                 # 接口
│   └── impl/CalcHistoryServiceImpl.java         # 实现（@Transactional）
├── mapper/calc/CalcHistoryMapper.java           # Mapper 接口
└── domain/calc/
    ├── CalcHistoryEntity.java                   # 继承 BaseEntity，Long 主键加 @JsonSerialize
    └── CalcComputeRequest.java                  # compute 入参 record（operator, first, second）
```

资源文件：
```
ai-devops/src/main/resources/
├── db/migration/V3__create_ai_devops_calc_history.sql   # 建表 + sys_menu 菜单/权限 SQL
└── mapper/calc/CalcHistoryMapper.xml                    # resultMap + 动态查询 + 软删除
```

前端（落 `ruoyi-ui-vue3`，参照高保真原型 CRUD 骨架 + 动态路由）：
```
ruoyi-ui-vue3/src/
├── api/ai-devops/calc.ts                  # listHistory/removeHistory/compute 接口封装
├── types/api/ai-devops/calc.ts            # TS 类型
└── views/ai-devops/calc/
    ├── index.vue                          # 按钮式计算器页
    └── history.vue                        # 历史列表页（查询/分页/软删除）
```

## Constitution Check

*GATE: Must pass before implementation. Re-check after design.*

对照 `memory/constitution.md` Principles I–VI + Security 段：

| Principle | Pass | 说明 |
|-----------|------|------|
| I. 模块隔离与上游零冲突 | ✅ | 全部业务代码落 `ai-devops` 模块 `com.ruoyi.aidevops.*.calc`，不改上游（ruoyi-system/admin 等）。复用经依赖 `ruoyi-common`。 |
| II. 注解化白名单 | N/A | 本功能需登录鉴权（非匿名），无白名单需求。不用 `@Anonymous`，也不用改 `SecurityConfig`。 |
| III. 菜单数据驱动 | ✅ | 计算器页/历史页/各按钮权限走 `sys_menu` 表（Flyway V3 插记录，`component` 指向 `ai-devops/calc/index` 等），前端动态路由，不改静态路由。 |
| IV. 分层单向依赖 | ✅ | Controller → Service → Mapper → Domain 单向；按功能域 `calc` 子包聚合各层。Controller 不直接调 Mapper。 |
| V. 单体多模块与简单性 | ✅ | 复用若依 `BaseController`/`AjaxResult`/`PageHelper`，不套额外抽象；运算逻辑直写在 Service，不引入策略模式等过度设计。 |
| VI. API 语义契约 | ✅ | compute 用 POST + `@RequestBody`（运算落库，非幂等有副作用，禁用 GET）；list 查询用 GET；remove 删除用 DELETE。方法语义对齐操作类型。 |

**凭据治理**（Security 段）：
- 本功能无密码/连接凭据；表 `ai_devops_calc_history` 符合 `ai_devops_` 前缀 + 审计字段（create_by/create_time/update_by/update_time/del_flag）。
- 结构变更走 Flyway（`V3__create_ai_devops_calc_history.sql`，业务表 V3 起编号，对齐 FlywayConfig baselineVersion=2）。

**结论**：全部通过，无违规需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/002-calculator/
├── spec.md              # 功能规范
├── plan.md              # 本文件（实施计划）
└── tasks.md             # 任务清单
```

### Source Code (repository root)

```text
AI-DevOps/
├── ai-devops/
│   ├── src/main/java/com/ruoyi/aidevops/
│   │   ├── controller/calc/CalcController.java
│   │   ├── service/calc/ICalcHistoryService.java
│   │   ├── service/calc/impl/CalcHistoryServiceImpl.java
│   │   ├── mapper/calc/CalcHistoryMapper.java
│   │   └── domain/calc/
│   │       ├── CalcHistoryEntity.java
│   │       └── CalcComputeRequest.java
│   └── src/main/resources/
│       ├── db/migration/V3__create_ai_devops_calc_history.sql
│       └── mapper/calc/CalcHistoryMapper.xml
└── ruoyi-ui-vue3/src/
    ├── api/ai-devops/calc.ts
    ├── types/api/ai-devops/calc.ts
    └── views/ai-devops/calc/{index,history}.vue
```

## Implementation Phases

1. **Phase 1 数据层**：Entity + Mapper 接口 + Mapper XML + Flyway V3（建表 + sys_menu）。
2. **Phase 2 服务层**：Service 接口 + 实现（运算逻辑 + 历史查询 + 软删除）。
3. **Phase 3 接口层**：Controller（四则运算 + 列表 + 软删除，权限 + 日志）。
4. **Phase 4 前端**：Vue3 计算器页 + 历史列表页 + API/TS 类型封装。
5. **Phase 5 测试 + 文档**：JUnit5 单测 + 同步 docs/（功能模块分析/目录结构/ER 图/README）。

## Assumptions / Open Questions

- 运算精度用 `Double`（spec 已界定范围），不引入 BigDecimal。
- 计算接口用 POST + JSON body（运算会创建历史记录，非幂等、有副作用，符合 RESTful 语义；不用 GET 路径参数——GET 期望安全幂等，但 compute 会落库）。
- `sys_menu` 的菜单 SQL 与建表 SQL 放同一个 V3 迁移文件（一次 Flyway 应用完成表 + 菜单）。
- 数据隔离用 `create_by = 当前登录用户` 实现（若依 `SecurityUtils.getUsername()`），不引入 `@DataScope`（YAGNI）。
