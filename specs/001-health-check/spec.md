# Feature Specification: 健康检查接口

**Feature Branch**: `001-health-check`

**Created**: 2026-08-18

**Status**: ✅ Implemented（已落地，追溯建档）

**Input**: User description: "需要一个无需登录即可访问的接口，用于验证 ai-devops 业务模块创建成功、Spring 上下文就绪、模块被启动类正确扫描。"

## User Scenarios & Testing *(mandatory)*

<!--
  User stories 按 user journey 优先级排序，每个 story 独立可测、可作为 MVP 切片。
-->

### User Story 1 - 开发者验证模块就绪 (Priority: P1)

作为开发者，我启动应用后想用浏览器直接访问一个接口，立即确认 ai-devops 模块已正确加载，而不必登录系统或调任何业务接口。

**Why this priority**: 这是 ai-devops 模块从零创建后的第一个验证点，确认 pom 注册、依赖引入、包扫描、Controller 注册全链路通畅。后续所有业务功能都依赖这个基线。

**Independent Test**: 单独访问该接口，不依赖任何登录态、不依赖数据库表、不依赖 Redis。返回模块名与状态即视为通过。

**Acceptance Scenarios**:

1. **Given** 应用启动成功（日志出现 `Started RuoYiApplication`，端口 8080），**When** 浏览器访问 `http://localhost:8080/aidevops/health` 不带任何认证头，**Then** HTTP 200，返回 `{"msg":"操作成功","code":200,"data":{"module":"ai-devops","status":"UP",...}}`
2. **Given** 应用启动且 `/aidevops/health` 标了 `@Anonymous`，**When** 访问未加白的接口（如 `/system/user/list`）不带 token，**Then** 被 Security 拦截（401/重定向登录），证明白名单是精确放行而非整体放行

---

### Edge Cases

- 应用未启动：连接被拒（非业务问题，不在本功能范围）
- 路径拼错（如 `/aidevops/healthx`）：404，符合预期
- 带无效 token 访问 `/aidevops/health`：仍应 200 放行（`@Anonymous` 优先于鉴权）

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 提供 `GET /aidevops/health` 接口，位于 `com.ruoyi.aidevops.controller.HealthController`。
- **FR-002**: 接口 MUST 免登录访问（白名单），通过 `@Anonymous` 注解实现，不修改 `SecurityConfig`。
- **FR-003**: 返回体 MUST 为若依标准 `AjaxResult`，`code=200`，`data` 含 `module`（值 `ai-devops`）、`status`（值 `UP`）、`timestamp`（当前毫秒）。
- **FR-004**: 时间戳 MUST 用 `System.currentTimeMillis()` 取值，不依赖外部时钟或 `new Date()`（保证任何环境稳定）。

### Key Entities *(include if feature involves data)*

本功能无数据实体（无数据库交互）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 浏览器直访 `http://localhost:8080/aidevops/health` 返回 200 与标准 `AjaxResult`，无需登录。
- **SC-002**: `mvn -pl ruoyi-admin -am -DskipTests clean package` 编译成功，ai-devops 作为依赖被 admin 正确解析打包。
- **SC-003**: 未加 `@Anonymous` 的接口仍被拦截，证明白名单精确生效。

## Assumptions

- 目标用户：开发者（非终端业务用户），故返回结构化 JSON 而非花哨页面。
- 范围边界：本功能仅验证模块就绪，不接入任何业务数据库表、不依赖 Redis。
- 复用系统：复用若依 `AjaxResult`、`@Anonymous` 机制，不自建返回结构与白名单逻辑。
