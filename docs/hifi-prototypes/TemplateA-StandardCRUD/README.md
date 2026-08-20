# 模版 A · 标准 CRUD 管理页（HTML 高保真交互原型）

> 通用 CRUD 管理页交互原型 · 业务示例：**用户管理** · 浏览器双击 `index.html` 即可运行

---

## 1. 模版概述

### 1.1 模版定位
- **通用 CRUD 管理页交互原型**：列表 → 表单 → 详情三页闭环。
- 所有交互细节均按真实生产页面打磨，可直接作为前端开发实现的视觉与行为参照。

### 1.2 业务示例
- 以 **用户管理** 为示例业务，覆盖 *输入框 / 下拉单选 / 下拉多选 / 日期 / 开关 / 自动生成只读字段 / 父子级联* 等典型表单控件。

### 1.3 技术方案
| 技术 | 版本 | 用途 |
|------|------|------|
| Vue.js | 2.6.14 | 数据绑定与组件化 |
| Element UI | 2.15.14 | UI 组件库 |
| SortableJS | 1.15.2 | 列配置拖拽排序 |
| 资源加载 | unpkg.com CDN | 无需 `npm install` |
| 协议 | `file://` | 双击 HTML 即可运行，无需启动本地服务器 |

---

## 2. 文件结构

```
TemplateA-StandardCRUD/
├── index.html          # 列表页：搜索 + 工具栏 + 表格 + 分页 + 列配置 + 导入
├── form.html           # 表单页：基本信息 / 详细信息 两个 Tab，新增 & 编辑通用
├── detail.html         # 详情页：基本信息 + 详细信息（可折叠）+ 操作历史
├── css/
│   └── style.css       # 自定义样式（面包屑链接色、复制图标、操作历史等）
├── js/
│   └── interactions.js # 30 条用户模拟数据 + 字段定义 + 35 条操作历史 + 字典选项
└── README.md           # 本文件
```

---

## 3. 列表页交互规则（`index.html`）

### 3.1 面包屑
- `首页（可点击）/ 用户管理 / 用户列表`
- 仅 **首页** 为蓝色超链接（点回 `index.html`）；当前节点不可点击。

### 3.2 搜索栏
- 顶部右上角「搜索 / 刷新 / 列配置」三枚圆形图标按钮，「搜索」按钮控制搜索栏整体显隐。
- **展开 / 收起机制**：
  - **默认显示字段**（4 个）：姓名、手机号、部门、状态。
  - **展开后追加字段**（3 个）：角色（多选）、性别、入职日期（日期范围）。
  - 通过最后一个 `el-form-item` 的「展开 / 收起」文本按钮切换 `searchExpanded`。
- **提示图标**：
  - 在「姓名 / 部门 / 角色」字段标签后，附 `el-icon-info` **灰色感叹号**。
  - hover 时显示 `el-tooltip` 文案；**仅做提示，不可点击跳转**。
- **搜索按钮**：`type="primary"`，文案「搜索」，回车键也可触发（`@keyup.enter.native="handleQuery"`）。
- **重置按钮**：默认样式，文案「重置」。

### 3.3 搜索逻辑（参数持久化）
- 点击「搜索」**不会清空** `queryParams`；条件持久保留，只把 `pageNum` 重置回第 1 页。
- 仅点击「重置」时调用 `this.$refs.queryForm.resetFields()` 真正清空所有搜索条件，并清空 `entryDateRange`、`roles` 等数组型字段。
- 数据过滤通过 `computed.filteredData` 实现，所见即所得。

### 3.4 工具栏按钮
按钮顺序固定：**新增 → 复制 → 修改 → 删除 → 导出 → 导入**（无「新手引导」按钮，已彻底移除）。

| 序号 | 按钮 | 类型 | 图标 | 禁用规则 | 禁用时 hover 提示 |
|------|------|------|------|---------|-------------------|
| 1 | 新增 | `primary plain`（蓝） | `el-icon-plus` | 始终可用 | — |
| 2 | 复制 | `warning plain`（橙） | `el-icon-document-copy` | 未选中或多选时禁用（`:disabled="single"`） | 未选：「请先选择一条数据」/ 多选：「只能选择一条数据进行复制」 |
| 3 | 修改 | `success plain`（绿） | `el-icon-edit` | 未选中或多选时禁用（`:disabled="single"`） | 未选：「请先选择一条数据」/ 多选：「只能选择一条数据进行修改」 |
| 4 | 删除 | `danger plain`（红） | `el-icon-delete` | 未选中任何行时禁用（`:disabled="multiple"`） | 「请先选择要删除的数据」 |
| 5 | 导出 | `warning plain`（橙） | `el-icon-download` | 始终可用 | — |
| 6 | 导入 | `info plain`（灰蓝） | `el-icon-upload2` | 始终可用 | — |

- **禁用按钮悬停提示实现方式**：使用 `<el-tooltip>` 包裹禁用的 `<el-button>`，外层套一层 `<span>` 让 tooltip 在 disabled 状态下仍能触发；动态计算 tooltip 文案（依据 `single` / `multiple` / 选中数量决定文案分支）。
- **复制按钮交互**：选中一行后点击「复制」 → 跳转 `form.html?mode=copy&id=xxx`，表单回填该行所有可编辑字段（不复制系统自动生成字段）。
- 选中行时顶部显示「已选 X 条」蓝色提示条，附「取消选择」文本按钮。

### 3.5 数据表格
- 模拟数据 **30 条**，多种状态 / 部门 / 角色组合。
- **所有列均可排序**：`sortable="custom"`，前端排序通过 `handleSortChange` 与 `computed.filteredData` 实现。
- **用户ID列为蓝色链接**：使用 `<el-link type="primary">`，点击跳转 `detail.html?id={id}`。
- 表格列默认设置：`border` / `size="medium"` / `highlight-current-row` / 多选列在最左。
- 单元格特殊渲染：手机号自动脱敏（`138****1234`）、部门 / 状态使用 `el-tag`、角色使用 plain `el-tag` 多标签。
- **操作列**：固定右侧、宽 220px、居中。**只保留业务操作**：
  - 「重置密码」（`el-icon-key`，`$confirm` 二次确认）
  - 「分配角色」（`el-icon-user`，提示打开弹窗）
  - **不放置编辑 / 删除 / 详情**（已通过工具栏 + ID 链接覆盖）。

### 3.6 删除
- 点击工具栏「删除」时弹出 `$confirm`，type=warning，附待删除用户姓名列表（HTML 渲染）。
- 确认后 **真正从数据中移除** 选中行，并：
  - 重置选择 / 多选禁用状态；
  - 若当前页码大于剩余总页数，自动跳到最后一页；
  - `$message.success('删除成功')`。

### 3.7 分页
- `el-pagination`，layout 含：`total / sizes / prev / pager / next / jumper`。
- **默认 10 条 / 页**，支持 `[10, 20, 50]` 切换。
- 切换 size 时回到第 1 页。

### 3.8 列配置（`Dialog` 弹窗形式）
- 点击右上角「列配置」圆形图标按钮（`el-icon-setting`）打开。
- **弹窗（Dialog）形式**，宽 550px，`close-on-click-modal=false`。
- 顶部蓝色提示条说明「拖拽调整顺序，开关控制显隐（至少保留一列可见）」。
- 列表区 `max-height: 400px`，超出滚动。
- 每行包含：
  1. **拖拽手柄**（`el-icon-rank`，基于 SortableJS 拖拽排序）；
  2. 列名；
  3. **宽度调节**：`el-input-number`，min=60 / max=500 / step=10；
  4. **显示 / 隐藏开关**：`el-switch`，至少保留一列可见的强校验（关闭最后一列时强制回退并 `$message.warning`）。
- 底部：「重置为默认」（二次确认）/「保存配置」。
- **保存后表格立即刷新**：通过深拷贝 `localColumns → columns` 并 `tableKey++` 强制 `el-table` 重新渲染。
- 关闭时销毁 SortableJS 实例。

### 3.9 导入弹窗
- 标题「导入用户数据」，宽 600px。
- 顶部 `el-alert info`，提示「请下载导入模板」+ 蓝色「下载导入模板」链接。
- **导入模式（分行显示 + 详细说明）**：
  - 单选组使用纵向布局（`flex-direction: column; gap: 12px`），保证两种模式分行展示；
  - **增量更新**：粗体标题 + 灰色说明文本「仅新增不存在的记录，更新已存在的记录，不删除现有数据」；
  - **全量覆盖**：红色粗体标题 + 红色说明文本「⚠ 先清空现有全部数据，再导入文件中的数据（危险操作，不可恢复）」。
- **上传区宽度 100% 铺满**：`<el-upload drag style="width:100%">`，CSS 中 `.import-dialog .el-upload-dragger { width: 100% }`。
- 限制：`.xlsx / .csv`，最多 1 个文件，单文件不超过 10MB（提示文案）。
- **全量模式二次确认**：提交前再弹一次「⚠ 危险操作」`$confirm`，确认后才执行。
- 提交后展示「导入结果」对话框（`Tabs` 分「成功 / 失败」两栏，失败行红底显示）。

---

## 4. 表单页交互规则（`form.html`）

### 4.1 面包屑
- `首页（可点击）/ 用户管理 / 用户列表（可点击）/ 用户列表表单`
- 「首页」「用户列表」均为蓝色超链接，跳回 `index.html`。

### 4.2 顶部规范
- **无返回按钮**、**无模式徽章**（不展示「新增 / 编辑」彩色标签）。
- 新增 / 修改 / 复制三种模式共用同一页面，仅通过 URL 参数 `?mode=add|edit|copy&id=xxx` 区分。
- **卡片 header 显示动态标题**（依据 `mode` 字段计算）：
  - `mode=add` → **「新增用户」**
  - `mode=edit` → **「修改用户」**
  - `mode=copy` → **「复制用户」**（基于已选行回填字段，但不带系统自动生成字段）
- 标题字号 16px、加粗，颜色 `#303133`；同时同步更新浏览器 `document.title`。

### 4.3 Tab 分组
单卡片内使用 `el-tabs` 二级分组，两栏栅格（`el-row :gutter=24` + `el-col :span=12`）：

| Tab 名称 | 字段 |
|---------|------|
| **基本信息** | 用户编号（自动生成）/ 姓名 / 手机号 / 邮箱 / 性别 / 状态 |
| **详细信息** | 部门 / 角色 / 出生日期 / 入职日期 / 是否启用 / 创建时间（自动生成） |

### 4.4 字段控件类型覆盖
| 控件类型 | 示例字段 | 实现 |
|---------|----------|------|
| 输入框 | 姓名 / 手机号 / 邮箱 | `el-input`，`maxlength + show-word-limit + clearable` |
| 下拉单选 | 性别 / 部门 / 状态 | `el-select`，`clearable filterable` |
| 下拉多选 | 角色 | `el-select multiple collapse-tags` |
| 日期 | 出生日期 / 入职日期 | `el-date-picker`，`value-format="yyyy-MM-dd"` |
| 开关 | 是否启用 | `el-switch`，含「启用 / 停用」文字 |
| 自动生成只读 | 用户编号 / 创建时间 | 灰色禁用 + 「系统自动生成」蓝色 `el-tag` |

### 4.5 自动生成只读字段
- `el-input disabled`，背景 `#F5F7FA`、文字 `#909399`、`cursor: not-allowed`。
- 输入框右侧紧跟 `el-tag size=mini type=info effect=plain`，文案默认「**系统自动生成**」。
- 新增模式：占位符为「（保存后自动生成）」/「（保存后自动填充）」；保存成功后由前端模拟生成最终值（部门前缀 + 4 位序号、当前时间）。

### 4.6 父子级联下拉
- 通过字段定义 `dependsOn` 描述依赖关系（角色字段 `dependsOn: 'deptId'`）。
- **未选父级时**：
  - 子级控件 `disabled = true`；
  - placeholder 动态变为 `请先选择【父级名称】`，例如「请先选择【部门】」。
- 父级变更触发 `@dept-change`：清空所有依赖该父级的子级字段，并刷新自动编号预览。

### 4.7 提示图标
- 字段标签后附 `el-icon-info` **灰色感叹号**。
- hover 显示 `el-tooltip` 帮助文案（来自字段定义的 `help` 属性）。
- 与列表页保持一致：仅作提示，不强求点击行为。

### 4.8 表单校验
- 必填项标签前红星，校验 trigger 按控件类型自动选 `change` / `blur`。
- 内置正则：手机号 `^1[3-9]\d{9}$`、邮箱 `type: 'email'`。
- 校验失败 `$message.error('请检查并完善标红字段')`。

### 4.9 底部按钮
- **固钉吸底**（`position: sticky; bottom: 0`），白底 + 顶部细边框 + 轻阴影。
- **仅 2 个按钮，居中显示**：
  - **保存（`type="primary"`，在左）**：触发表单校验 → 模拟保存 → `$message.success('保存成功')` → **停留当前页**（新增场景下自动回填用户编号与创建时间）。
  - **关闭（默认样式，在右）**：直接 `window.location.href = 'index.html'` **返回列表页**。

---

## 5. 详情页交互规则（`detail.html`）

### 5.1 面包屑
- `首页（可点击）/ 用户管理 / 用户列表（可点击）/ 用户详情`

### 5.2 顶部信息栏
- 左侧：用户姓名（24px 粗体）+ 状态 `el-tag` + 部门 `el-tag`；下方副信息行展示「ID #xxx ｜ 用户编号：xxx ｜ 创建于 xxx」。
- 右侧：**仅一枚「返回列表」按钮**（`type="primary" plain"`，`el-icon-back`，`@click="goBack"` 跳回 `index.html`）。
- **无编辑 / 复制按钮**（编辑入口由列表页工具栏覆盖）。

### 5.3 字段分组
- **基本信息**（默认展开，无折叠交互）：用户ID / 用户编号 / 姓名 / 手机号 / 邮箱 / 性别 / 状态。
- **详细信息**（可折叠，**默认展开**）：部门 / 角色 / 出生日期 / 入职日期 / 是否启用 / 创建时间。
  - 标题行右侧显示「6 个字段」灰色计数；
  - 整行可点击，箭头 `el-icon-arrow-down ↔ el-icon-arrow-right` 切换；
  - 折叠动画使用 `el-collapse-transition`。

### 5.4 列宽对齐（高保真规范）
两个 `el-descriptions` 通过统一 CSS 实现 **上下完全对齐**：
- `table-layout: fixed !important; width: 100%`；
- 标签列 `width: 10%`，`white-space: nowrap`，避免换行；
- 内容列 `width: 23.33%`（`(100% - 10%) / 3 ≈ 23.33%`，对应 3 列布局 `:column="3"`）。
- 即使两表格字段数量不同，标签 / 内容列宽仍严格对齐。

### 5.5 复制图标
- 在每个字段值末尾内嵌 `el-icon-document-copy` 复制图标，**默认 `opacity: 0` 隐藏**。
- 当鼠标 hover 字段值容器（`.field-value`）时，`opacity: 1` 显示。
- hover 图标本身时显示 `el-tooltip` 文案 **「点击复制」**。
- 点击调用 `doCopy(text)`：优先 `navigator.clipboard.writeText`，失败回退 `document.execCommand('copy')`，成功 `$message.success('复制成功')`，失败 `$message.warning('复制失败，请手动选取')`。
- 状态 / 部门 / 角色等 `el-tag` 字段同样支持复制（复制纯文本标签内容）。

### 5.6 字段标签提示图标
- **用户ID** 字段标签后带灰色感叹号 `el-icon-info`，hover 显示「系统唯一标识，不可修改」。
- 其他字段保持简洁，不附图标。

### 5.7 操作历史
- 模拟数据共 **35 条**：34 条字段修改记录 + 1 条创建记录（最末位，时间最早）。
- **默认显示最近 10 条**，按时间倒序排列。
- 列表底部「**加载更多（还有 N 条）**」文本按钮，每次点击 +10 条；全部加载后显示「已全部加载」灰字。
- **历史项格式（修改）**：
  ```
  {时间}  【{操作人}】修改了 【{字段}】 字段值，修改之前值为 【{旧值}】，修改之后值为 【{新值}】
  ```
- **历史项格式（创建）**：
  ```
  {时间}  【{操作人}】创建了用户
  ```
- **样式约定**：
  - 时间：`#909399` 灰色 + 等宽字体 Menlo / Monaco / Consolas。
  - 操作人：`#409EFF` 蓝色加粗。
  - 字段名：`#E6A23C` 橙色加粗。
  - **旧值：`#F56C6C` 红色（无删除线）**。
  - **新值：`#67C23A` 绿色加粗**。
- 列表项之间使用虚线分隔（`border-bottom: 1px dashed #EBEEF5`）。

---

## 6. 通用规范

### 6.0 全局视觉规范
- **页面背景色**：纯白 `#ffffff`（包含 `body`、`.app-main`、各业务页根容器），不再使用浅灰底色，保证卡片与背景一致的现代感。
- **卡片**：白底 + 1px 浅边框（`#EBEEF5`）+ 轻阴影（`box-shadow: 0 2px 12px rgba(0,0,0,.04)`）。
- **图标按钮**：搜索 / 刷新 / 列配置等顶栏圆形图标按钮统一灰色描边 + hover 蓝色高亮。
- **提示图标**：所有 `el-icon-info` 灰色感叹号仅作 hover 提示，**不可点击跳转**。

### 6.1 模拟数据
- **30 条用户模拟数据**：姓 / 名 / 邮箱域名循环组合；6 个部门（TECH / PROD / OPS / MKT / FIN / HR）轮询；男女交替；每 7 条混入 1 条「停用」状态；6 种角色组合循环；手机号脱敏显示（`138****1234`）。
- **35 条操作历史**：时间倒序，跨度合理（每条间隔 12~47 小时随机扰动），覆盖 11 种字段变更类型。

### 6.2 搜索参数持久化
- 搜索后条件保留，便于返回详情后回到列表仍维持原筛选；
- 仅「重置」按钮显式清空。

### 6.3 消息提示
- 成功：`this.$message.success('xxx')`
- 警告：`this.$message.warning('xxx')`
- 错误：`this.$message.error('xxx')`
- 普通：`this.$message.info('xxx')`

### 6.4 确认弹窗
- 统一使用 `this.$confirm(content, title, options)`：
  - 删除 / 重置密码 / 重置列配置 / 全量覆盖导入：`type: 'warning'`；
  - 导出确认：`type: 'info'`；
  - 删除支持 HTML 富文本（`dangerouslyUseHTMLString: true`）。

### 6.5 权限控制说明
- 在真实接入 RuoYi-Vue 框架时，按钮应使用 `v-hasPermi="['system:user:add']"` 等指令做权限控制。
- 原型仅做交互演示，未植入指令；落地实现时按业务模块替换权限标识即可。

---

## 7. 使用方式

### 7.1 浏览器直接打开
```bash
# 在终端定位到原型目录后
open index.html

# 或在 Finder / 资源管理器中双击 index.html
```
> 支持 `file://` 协议，无需启动本地服务器。

### 7.2 CDN 依赖说明
所有依赖通过 unpkg CDN 加载，**首次打开需联网**：
```html
<link  rel="stylesheet" href="https://unpkg.com/element-ui@2.15.14/lib/theme-chalk/index.css">
<script src="https://unpkg.com/vue@2.6.14/dist/vue.min.js"></script>
<script src="https://unpkg.com/element-ui@2.15.14/lib/index.js"></script>
<script src="https://unpkg.com/sortablejs@1.15.2/Sortable.min.js"></script>
```
- 内网环境部署时，可将上述 4 个文件下载到本地相对路径替换。
- 业务逻辑与字段配置集中在 `js/interactions.js`，便于复用到其他业务模版。
