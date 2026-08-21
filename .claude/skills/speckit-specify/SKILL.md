---
name: speckit-specify
description: Create or update the feature specification from a natural language feature description. 从自然语言描述写 specs/NNN-slug/spec.md（用户故事 + 验收标准，聚焦 WHAT/WHY，无技术细节）。
argument-hint: Describe the feature you want to specify
user-invocable: true
disable-model-invocation: false
---

# speckit-specify：编写功能规范 spec.md

本命令把用户输入的功能描述，转成 `specs/NNN-slug/spec.md`（规范驱动开发 SDD 的第一步——**做什么、为什么**，不涉技术栈）。遵循 [spec-kit](https://github.com/github/spec-kit) 的 specify 命令逻辑，适配本项目路径（`memory/constitution.md` + `specs/NNN-slug/`，无 `specify` CLI 依赖）。

## User Input

```text
$ARGUMENTS
```

你 **必须** 先考虑用户输入（若非空）再继续。`$ARGUMENTS` 即触发命令时用户在 `/speckit.specify` 后输入的功能描述文本。

## 执行流程

### 1. 生成 short name

分析功能描述，提取关键词，生成 2-4 词的 short name（action-noun 格式，保留技术术语如 OAuth2/JWT）：
- "我要加用户认证" → `user-auth`
- "实现 API 的 OAuth2 集成" → `oauth2-api-integration`
- "做一个分析仪表盘" → `analytics-dashboard`

### 2. 定位 feature 目录（替代 spec-kit 的 feature.json）

本项目不用 `.specify/feature.json`，按 `specs/README.md` 约定：

1. 扫 `specs/` 下现有目录，取最大编号 N。
2. 下一编号 = N+1，零填充到 3 位（`001`、`002`…超 999 自动扩展为 `1000`）。
3. 拼 `<NNN>-<short-name>` 作为 feature 目录，如 `002-user-auth`。
4. `mkdir -p specs/<NNN>-<short-name>`。
5. 设 `FEATURE_DIR=specs/<NNN>-<short-name>`，`SPEC_FILE=$FEATURE_DIR/spec.md`。

> 一次调用只建一个 feature。feature 目录名与 git 分支名独立（分支命名见 constitution Workflow 段）。

### 3. 读基线

- 读 `memory/constitution.md`，理解治理原则（虽 spec 不写技术细节，但 Success Criteria 须技术无关、Key Entities 须符合业务表 `ai_devops_` 前缀约定等）。
- 读 `specs/README.md` 确认命名与结构约定。
- 若想参考格式，读 `specs/001-health-check/spec.md`（样板）。

### 4. 生成 spec（写入 SPEC_FILE）

spec 聚焦 **WHAT 用户要 + WHY**，**避免 HOW**（不写语言、框架、API、代码结构）。按以下结构写入 `spec.md`（对齐 `specs/001-health-check/spec.md`）：

```markdown
# Feature Specification: <功能名>

**Feature Branch**: `<NNN>-<short-name>`
**Created**: <今日日期>
**Status**: 📋 Specified
**Input**: User description: "<原始描述>"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - <故事名> (Priority: P1)

作为 <角色>，我 <想要什么>，以便 <价值>。

**Why this priority**: <为什么这是 P1 / MVP 切片>
**Independent Test**: <如何独立验证此 story，不依赖其他 story>

**Acceptance Scenarios**:
1. **Given** <前置>, **When** <动作>, **Then** <预期>
2. ...（每个 story 至少一个验收场景）

### Edge Cases
- <边界情况 1>
- <边界情况 2>

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: System MUST <可测需求>
- **FR-002**: ...

### Key Entities *(include if feature involves data)*
- <实体名>：<字段>...（若涉及数据库，实体表用 ai_devops_ 前缀）

## Success Criteria *(mandatory)*

### Measurable Outcomes
- **SC-001**: <可度量、技术无关的成果，如"用户能在 3 分钟内完成 X">
- **SC-002**: ...

## Assumptions
- <假设的默认决策，如"目标用户是开发者">
- <范围边界，如"本功能不接入 Y">
```

### 5. 质量校验（spec-kit 核心质量门）

写完初稿后，对照以下清单校验（在 `FEATURE_DIR/checklists/requirements.md` 生成校验清单）：

**内容质量**：
- [ ] 无实现细节（语言/框架/API）
- [ ] 聚焦用户价值与业务需求
- [ ] 面向非技术干系人
- [ ] 所有 mandatory 段完成

**需求完整性**：
- [ ] 无残留 [NEEDS CLARIFICATION] 标记
- [ ] 需求可测、无歧义
- [ ] Success Criteria 可度量且技术无关
- [ ] 验收场景齐全，边界情况已识别
- [ ] 范围边界清晰，依赖与假设已记录

**质量门规则**：
- 对模糊处先做**合理默认推断**（基于上下文与行业标准），只有满足以下全部才标 `[NEEDS CLARIFICATION: 问题]`：① 显著影响范围或体验 ② 多种合理解读有不同后果 ③ 无合理默认。
- **最多 3 个 NEEDS CLARIFICATION**。超过则保留 3 个最关键的（优先级：范围 > 安全 > 体验 > 技术细节），其余做默认推断。
- 若有 NEEDS CLARIFICATION，向用户呈现选项（每个问题给 A/B/C + 含义 + Custom），等用户答复后写回 spec，再校验。
- 校验未过项自动修正 spec（最多 3 轮），仍不过则在清单备注记录并告知用户。

### 6. 完成报告

向用户报告：
- `FEATURE_DIR`（feature 目录路径）
- `SPEC_FILE`（spec 文件路径）
- 质量校验结果摘要
- 是否 ready for 下一阶段

## 下一步建议

spec 写完且校验通过后，运行 **`/speckit.plan`** 把规范翻译成技术方案 + Constitution Check。
