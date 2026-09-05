---
description: "Task list for 计算器 feature implementation"
---

# Tasks: 计算器

**Input**: Design documents from `/specs/002-calculator/`
**Prerequisites**: plan.md (required), spec.md (required for user stories + AC 验收用例)
**Tests**: JUnit5 + Mockito 单元测试，覆盖运算逻辑（AC-1.1~1.3）与 Service 历史查询/软删除（AC-2.1~2.6）；前端手动验证。
**Organization**: Tasks 按 user story + 分层组织，便于独立实现与测试。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可与同 phase 内其他任务并行（不同文件、无依赖）
- **[Story]**: 该任务属于哪个 user story（US1 计算 / US2 历史）
- 描述中含确切文件路径

## Path Conventions

- 后端模块：`ai-devops/src/main/java/com/ruoyi/aidevops/...`
- Mapper XML：`ai-devops/src/main/resources/mapper/calc/`
- Flyway 迁移：`ai-devops/src/main/resources/db/migration/V{N}__<desc>.sql`（业务表 V3 起）
- 前端：`ruoyi-ui-vue3/src/{api,types,views}/ai-devops/`
- 根 pom：`pom.xml`，启动模块 pom：`ruoyi-admin/pom.xml`

---

## Phase 1: 数据层（Entity + Mapper + Flyway）

**Purpose**: 建表、菜单 SQL、实体、Mapper 接口与 XML

- [x] T001 [P] [US2] 新增 `ai-devops/src/main/resources/db/migration/V3__create_ai_devops_calc_history.sql`：建表 `ai_devops_calc_history`（字段见 plan 数据模型，含审计字段 + del_flag）+ 插入 `sys_menu` 菜单记录（计算器页 M、历史页 M、compute/list/remove 三按钮 F，权限标识 `aidevops:calc:*`，component 指向 `ai-devops/calc/index`、`ai-devops/calc/history`）
- [x] T002 [P] [US1] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/domain/calc/CalcHistoryEntity.java`：继承 `BaseEntity`，字段 calcId(Long 加 `@JsonSerialize(ToStringSerializer)`) / firstNumber / secondNumber / operator / result / status / delFlag，完整 JavaDoc（`@author shandongdong`）。同目录补 `CalcComputeRequest.java`：Java 17 record，承载 compute 入参 `(operator, first, second)`
- [x] T003 [P] [US2] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/mapper/calc/CalcHistoryMapper.java`：接口，方法 `insertCalcHistory` / `selectCalcHistoryList` / `deleteCalcHistoryByIds`（软删除 update）
- [x] T004 [US2] 新增 `ai-devops/src/main/resources/mapper/calc/CalcHistoryMapper.xml`：resultMap + `<sql>` 片段 + selectList（`del_flag IN ('0')` + operator/createTime 动态过滤 + `create_by=#{createBy}` 隔离）+ insert + 软删除 update（`del_flag='2'` + update_by/update_time）。T004 依赖 T003 接口定义

**Checkpoint**: 表结构与菜单 SQL 就绪，Mapper 接口+XML 定义完整，可被 Service 调用。

---

## Phase 2: 服务层（Service 接口 + 实现）

**Purpose**: 运算逻辑 + 历史查询/软删除业务

**⚠️ CRITICAL**: Phase 1 数据层完成前不得开始（依赖 Entity/Mapper）

- [x] T005 [P] [US1] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/service/calc/ICalcHistoryService.java`：接口，方法 `compute(operator, first, second)` 返回 `CalcHistoryEntity`、`selectCalcHistoryList(entity)`、`deleteCalcHistoryByIds(ids)`
- [x] T006 [US1] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/service/calc/impl/CalcHistoryServiceImpl.java`：实现 `ICalcHistoryService`，`@Service` + `@Transactional`。compute 按 operator switch 四则运算，除零抛 `ServiceException("除数不能为0")` 不落库，合法运算设 createBy(SecurityUtils.getUsername()) + 调 mapper.insert；selectList 带用户隔离；delete 软删除（置 del_flag + updateBy）。T006 依赖 T005 接口

**Checkpoint**: 运算与历史业务逻辑可独立测试（mock Mapper）。

---

## Phase 3: 接口层（Controller）

**Purpose**: 暴露 REST 接口 + 权限 + 日志

- [x] T007 [US1] 新增 `ai-devops/src/main/java/com/ruoyi/aidevops/controller/calc/CalcController.java`：`@RestController` + `@RequestMapping("/aidevops/calc")` + extends `BaseController`。compute 接口 `@PostMapping("/compute")` + `@RequestBody CalcComputeRequest`（RESTful：运算落库非幂等，用 POST）+ `@PreAuthorize("@ss.hasPermi('aidevops:calc:compute')")` + `@Log`，返回 `AjaxResult.success(entity)`；list `@GetMapping("/list")` + `@PreAuthorize('aidevops:calc:list')` + startPage/getDataTable；remove `@DeleteMapping("/{calcIds}")` + `@PreAuthorize('aidevops:calc:remove')` + `@Log(DELETE)`。T007 依赖 T006 Service

**Checkpoint**: 三接口可用，权限/日志就位，可被前端调用。

---

## Phase 4: 前端（Vue3 + TS + Element Plus）

**Purpose**: 计算器页 + 历史列表页

- [x] T008 [P] [US1] 新增 `ruoyi-ui-vue3/src/types/api/ai-devops/calc.ts`：TS 类型 `CalcHistory`（字段对齐 Entity）+ `CalcHistoryQueryParams`
- [x] T009 [P] [US1] 新增 `ruoyi-ui-vue3/src/api/ai-devops/calc.ts`：`compute(operator, first, second)` / `listHistory(query)` / `delHistory(ids)`，走 `@/utils/request`
- [x] T010 [US1] 新增 `ruoyi-ui-vue3/src/views/ai-devops/calc/index.vue`：`<script setup lang="ts" name="Calc">`，按钮式计算器（数字键+运算符+等号+C/DEL），调 compute 接口回显结果，等号防抖。参照旧项目 Vue2 计算器交互，Element Plus 按钮组件重写。T010 依赖 T009 API
- [x] T011 [US2] 新增 `ruoyi-ui-vue3/src/views/ai-devops/calc/history.vue`：`<script setup lang="ts" name="CalcHistory">`，历史列表页（分页 + 运算符/时间过滤 + 多选软删除），参照高保真原型 `TemplateA-StandardCRUD` 列表骨架 + Element Plus Table/Pagination。T011 依赖 T009 API

**Checkpoint**: 前端两页可独立验证（登录后访问计算器页计算、历史页查看删除）。

---

## Phase 5: 单元测试（JUnit5 + Mockito）

**Purpose**: 验收测试用例 AC 落地为可执行测试

- [x] T012 [P] [US1] 新增 `ai-devops/src/test/java/com/ruoyi/aidevops/service/calc/CalcHistoryServiceImplTest.java`：测试 compute 四则运算正确性（AC-1.1：6+2=8；AC-1.2：减/乘/除）+ 除零抛 ServiceException 不落库（AC-1.3，verify mapper never called）；mock CalcHistoryMapper
- [x] T013 [P] [US2] 同上测试文件追加：selectCalcHistoryList 带用户隔离（AC-2.5 create_by 过滤）+ 软删除置 del_flag（AC-2.2 verify update 调用）+ 已删记录不返回（AC-2.6 mock 返回 del_flag='2' 不在列表）。T013 可与 T012 同文件并行编写

**Checkpoint**: `mvn -pl ai-devops test` 全绿，AC-1.1~1.3、AC-2.2/2.5/2.6 被自动化覆盖。

---

## Phase 6: 文档同步（constitution 质量门，强制）

**Purpose**: 改代码同步更新 docs/ + specs/

- [x] T014 [P] [US1] 更新 `docs/design-docs/功能模块分析.md`：补 ai-devops 模块计算器功能行
- [x] T015 [P] [US1] 更新 `docs/ARCHITECTURE.md`：补 ai-devops 的 controller/service/mapper/domain/calc 子包树 + 前端 ai-devops/calc 目录
- [x] T016 [P] [US2] 更新 `docs/design-docs/数据库ER关系图.md`：补 `ai_devops_calc_history` 表（若有业务表关系则补）
- [x] T017 [P] [US1] 更新 `specs/README.md`：已有功能表加 002 计算器行（状态 ✅ Implemented）
- [x] T018 [US1] 更新 `specs/002-calculator/spec.md`：Status 改 ✅ Implemented

**Checkpoint**: 文档与代码一致，质量门通过。

---

## Dependencies & Execution Order

### Phase Dependencies
- Phase 1（数据层）：无依赖，可立即开始（T001/T002/T003 并行，T004 串行）
- Phase 2（服务层）：依赖 Phase 1（CRITICAL）
- Phase 3（接口层）：依赖 Phase 2
- Phase 4（前端）：依赖 Phase 3 接口可用（T008/T009 可提前并行，T010/T011 依赖 API）
- Phase 5（测试）：依赖 Phase 2（测 Service，可与 Phase 3/4 并行）
- Phase 6（文档）：依赖代码完成

### Parallel Opportunities
- Phase 1：T001/T002/T003 互不冲突，`[P]` 并行
- Phase 4：T008/T009 并行（类型+API）
- Phase 5：T012/T013 同测试文件，可并行编写
- Phase 6：T014~T018 文档更新互不冲突，`[P]` 并行

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Phase 1 数据层（T001~T004）
2. Phase 2 服务层（T005~T006）— CRITICAL
3. Phase 3 接口层（T007）
4. **STOP and VALIDATE**: US1 独立测试（登录后计算器页算四则，验证 AC-1.1~1.6）
5. Phase 4 前端 + Phase 5 测试 + Phase 6 文档（US2 跟进）

> US1 是 MVP 切片（四则运算 + 落库），US2（历史管理）在 MVP 验证后跟进。两者都属本功能，同一迭代交付。

## Notes

- [P] 任务 = 不同文件、无依赖
- [Story] 标签把任务映射到 user story，便于追溯 AC 验收用例
- 每个 task 或逻辑组完成后提交（commit `type(scope):` 中文，不加 Co-Authored-By）
- 任何 checkpoint 可停下独立验证 story
- 验收用例 AC-x.x 在 Phase 5 落地为 JUnit5 测试，确保需求→测试可追溯
- 避免：模糊任务、同文件冲突、破坏独立性的跨 story 依赖
