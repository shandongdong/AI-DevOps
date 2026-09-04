# Feature Specification: 计算器

**Feature Branch**: `002-calculator`

**Created**: 2026-08-29

**Status**: ✅ Implemented

**Input**: User description: "基于 SDD 规范完成一个计算器小功能，包含完整前端、后端、数据库、Flyway、单元测试，端到端交付。参考旧项目 ct-backend 的计算器四件套（entity/service/mapper/controller）与 ct-frontend 的 Vue2 计算器页，但需补充真实数据库操作、改用本项目技术栈（Vue3 + Element Plus + TS + Pinia）。重点验证 SDD 规范，代码不需太复杂。"

> **需求编写规范**：本 spec 的用户故事遵循敏捷 **3C 原则**（Card 卡片简述 / Conversation 对话详述 / Confirmation 验收确认）与 **INVEST 原则**。每个故事含标准格式（作为一个<角色>，我希望<行为>，以便于<价值>）、INVEST 评估、编号化验收测试用例（AC-x.x，Given/When/Then，可追溯至 JUnit5）。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 四则运算（MVP）

**Card**：作为一个系统用户，我希望在计算器页面输入两个数并执行加/减/乘/除运算，以便于快速得到准确结果而无需离开系统。

**Conversation**：
- 用户已登录，持有 `aidevops:calc:compute` 权限。
- 页面是按钮式计算器（参照旧项目 Vue2 交互：数字键 + 运算符键 + 等号键），但用本项目 Vue3 + Element Plus 重写。
- 用户输入第一个数 → 选择运算符 → 输入第二个数 → 按等号，前端调后端计算接口，回显结果。
- 每次合法运算成功后，系统自动落库一条历史记录（运算式、结果、运算人、时间）。
- 除数为 0 是明确边界：返回友好业务错误（非 500），不落库脏数据。
- 非数字输入由前端拦截或后端校验，不发起计算。

**Confirmation（验收测试用例）**：

- **AC-1.1**：**Given** 用户已登录且有 `aidevops:calc:compute` 权限，**When** 输入 6、选 `+`、输入 2、按等号，**Then** 页面显示结果 8，历史表新增一条（6, 2, +, 8）。
- **AC-1.2**：**Given** 同上，**When** 依次执行减（6,2→4）、乘（6,2→12）、除（6,2→3），**Then** 每次结果正确且各新增一条历史。
- **AC-1.3**：**Given** 执行除法且除数为 0，**When** 按等号，**Then** 返回业务错误提示"除数不能为0"（HTTP 200 + 错误码，非 500），历史表不新增。
- **AC-1.4**：**Given** 输入非数字内容，**When** 提交运算，**Then** 提示"请输入有效数字"，不发起后端请求。
- **AC-1.5**：**Given** 未登录，**When** 直接访问 `/aidevops/calc/compute/*`，**Then** 被 Security 拦截（401）。
- **AC-1.6**：**Given** 登录但无 `aidevops:calc:compute` 权限，**When** 调用计算接口，**Then** 被权限拦截（403/无权限提示）。

**INVEST 评估**：
- **I 独立**：本故事可独立完成与测试，不依赖历史管理故事。
- **N 可协商**：运算符 UI 符号（×÷ vs */）、接口风格（GET 路径参数 vs POST body）可协商，spec 不锁死实现。
- **V 有价值**：核心功能——让用户在系统内完成计算。
- **E 可估算**：四则运算 + 一张表 + 一个接口，工作量可估。
- **S 小**：单一接口 + 单页，一个迭代可完成。
- **T 可测试**：AC-1.1~1.6 均为可执行验收用例。

### User Story 2 - 计算历史管理

**Card**：作为一个系统用户，我希望查看自己的计算历史列表并能删除记录，以便于回顾运算过程与清理无用数据。

**Conversation**：
- 依赖 US1 先产生历史记录（US2 是 R+D，US1 是 C+Create）。
- 历史列表页分页展示（每页 10 条），含运算式、结果、运算时间。
- 查询支持按运算符（+/-/*//）与时间范围过滤。
- 数据按当前登录用户隔离——用户只能看自己的记录，看不到他人。
- 删除为软删除（置 `del_flag`，不物理删除），支持单条与批量。
- 列表页参照项目高保真原型 `TemplateA-StandardCRUD` 的列表布局。

**Confirmation（验收测试用例）**：

- **AC-2.1**：**Given** 历史表有该用户 N 条记录，**When** 打开历史列表页，**Then** 默认分页展示（每页 10 条），含运算式、结果、运算时间列。
- **AC-2.2**：**Given** 列表选中一条或多条记录，**When** 点删除，**Then** 记录被软删除（`del_flag` 置 '2'），列表不再显示，但库内仍保留。
- **AC-2.3**：**Given** 查询条件输入运算符 `+`，**When** 查询，**Then** 仅返回运算符为 `+` 的记录。
- **AC-2.4**：**Given** 查询条件输入时间范围（如 2026-08-01 至 2026-08-31），**When** 查询，**Then** 仅返回该时间范围内的记录。
- **AC-2.5**：**Given** 用户 A 登录，**When** 查询历史，**Then** 只看到自己 `create_by=A` 的记录，看不到用户 B 的。
- **AC-2.6**：**Given** 已软删除的记录，**When** 再次查询列表，**Then** 不出现（查询过滤 `del_flag IN ('0')`）。

**INVEST 评估**：
- **I 独立**：与 US1 解耦——只要有数据可测，US2 的列表/删除可独立实现。
- **N 可协商**：分页大小、查询条件字段可协商。
- **V 有价值**：历史回溯 + 数据治理（软删除）。
- **E 可估算**：标准列表+查询+软删除，模式成熟可估。
- **S 小**：一页一接口，规模可控。
- **T 可测试**：AC-2.1~2.6 均可执行验证。

### Edge Cases

- 除数为 0：返回友好错误，不入库（见 AC-1.3）。
- 输入超长数字/科学计数法：后端 `Double` 接收，超范围按异常提示。
- 并发连续点击等号：前端防抖/禁用按钮，避免重复落库。
- 未登录访问任一 `/aidevops/calc/*`：401（见 AC-1.5）。
- 无权限用户执行操作：权限拦截（见 AC-1.6）。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 提供四则运算接口（加/减/乘/除），接收两个数值，返回运算结果，路径前缀 `/aidevops/calc`。
- **FR-002**: System MUST 在每次合法运算成功后，将运算式、结果、运算人、运算时间落库（表 `ai_devops_calc_list`）。
- **FR-003**: System MUST 拦截除数为 0，返回业务错误提示（非 500 异常），且不落库。
- **FR-004**: System MUST 提供历史记录列表查询接口，支持分页与按运算符/时间范围过滤，且按当前登录用户隔离数据（只能看自己的记录）。
- **FR-005**: System MUST 提供历史记录删除接口（软删除，置 `del_flag`，不物理删除），支持单条与批量。
- **FR-006**: System MUST 对计算与历史接口做权限控制，权限标识以 `aidevops:calc:` 为前缀（如 `aidevops:calc:compute` / `aidevops:calc:list` / `aidevops:calc:remove`）。
- **FR-007**: System MUST 通过 `sys_menu` 表注册菜单与权限按钮（计算器页、历史页、各操作按钮），前端动态路由，不写静态业务路由。

### Key Entities *(include if feature involves data)*

- **ai_devops_calc_list**：计算历史记录表
  - `calc_id` bigint 主键（自增）
  - `first_number` double 第一个数
  - `second_number` double 第二个数
  - `operator` char(1) 运算符（统一存 + - * / 四则符号）
  - `result` double 运算结果
  - 审计字段：`create_by` / `create_time` / `update_by` / `update_time` / `del_flag` / `remark`

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 登录用户能在计算器页完成四则运算并看到正确结果，每次成功运算在历史表新增一条（验证 AC-1.1~1.4）。
- **SC-002**: 用户能在历史列表页分页查看自己的记录，按运算符/时间过滤，软删除后记录不再显示但库内保留（验证 AC-2.1~2.6）。
- **SC-003**: 全程不登录访问任一 `/aidevops/calc/*` 接口均被 401 拦截；无对应权限的用户执行操作被权限拦截（验证 AC-1.5~1.6）。
- **SC-004**: 单元测试覆盖核心运算逻辑（含除零）与 Service 层历史查询/软删除，`mvn test` 全绿。
- **SC-005**: 数据库表结构与菜单由 Flyway 迁移统一创建，新库旧库都能正确应用。

## Assumptions

- 目标用户：已登录的系统用户（计算器非匿名功能，与参考旧项目的免登录不同——本功能要验证权限与菜单数据驱动）。
- 运算精度：用 `Double` 足够教学场景，不处理 BigDecimal 级金融精度（范围边界）。
- 复用系统：复用若依 `AjaxResult` / `TableDataInfo` / `BaseController` / `@PreAuthorize` / `BaseEntity`，不自建返回结构与分页逻辑。
- 前端复用项目既有 `@/utils/request`、`@/components`（分页等）、动态路由体系，参考 `docs/hifi-prototypes/TemplateA-StandardCRUD/` 做列表页骨架。
- 计算器页交互参照旧项目 Vue2 计算器（按钮式输入 + 运算符 + 等号），但用 Vue3 `<script setup lang="ts">` + Element Plus 重写。
- 验收测试用例（AC-x.x）在 implement 阶段映射为 JUnit5 单元测试，可追溯。
