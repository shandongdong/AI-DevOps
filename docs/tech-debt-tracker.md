# Tech Debt Tracker — 技术债追踪

> 本文件记录本项目已知的技术债、待补的 sensor/harness 缺口、已否决的方案。呼应 OpenAI 铁律 2「代码仓库即记录系统」——散在对话/人脑的技术债必须落仓库，否则对 agent 来说等于不存在。

## Harness sensor 缺口

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | P2-b 前端 UI sensor | 未做 | 把 Playwright MCP 接成"改前端→自动验证"反馈回路。属 Behaviour harness 维度（最难补的一块）。对应 OpenAI 把 Chrome DevTools 协议接入 agent 运行时。 |
| 2 | 原则 II 注解化白名单机械强制 | 未做 | `@Anonymous` 用法靠 review，未机械检查。不易用文件路径 matcher 拦（靠代码模式），后续按需。 |
| 3 | 原则 III 菜单数据驱动机械强制 | 未做 | 菜单走 `sys_menu` 靠 review，未检查是否改了前端静态路由。同上，靠代码模式不易 matcher 拦。 |
| 4 | PMD/Checkstyle 接进 mvn test | 未做 | 重复代码/圈复杂度/缺测试覆盖全没覆盖。低垂果实，后续可补。属 Maintainability harness 维度。 |
| 5 | /speckit-scan 自动化/定期跑 | 未做 | 当前手动调起。先验证价值再考虑 cron 自动化（YAGNI）。稳定后接 CI 定期扫。 |
| 6 | /speckit-scan 扫描模式补盲 | 考虑中 | 首轮漏扫 `.java` 类名对不上（只 grep `*.xml`），重扫补上。Inferential sensor 需实战迭代完善扫描模式。 |

## 已否决的方案（记录决策，避免重复讨论）

| # | 项 | 决策 | 理由 |
|---|---|---|---|
| 1 | specs/ 加 active/completed 分类 | 否决 | 用户明确说不用区分状态。specs/README 的状态表已够用。 |
| 2 | 拆 项目开发规范.md 为 DESIGN.md/SECURITY.md 等根目录单文件 | 否决 | 项目规模未到拆分临界。当前按语义命名集中一份已够用（YAGNI）。 |
| 3 | 自动生成 docs/generated/db-schema.md | 否决 | 手写 ER 图 + /speckit-scan 已能查对不上。自动生成属可选增强，非必需。 |
| 4 | docs/exec-plans/active + completed 目录结构 | 否决 | 同 #1，不区分状态。 |

## 架构/规范债

| # | 项 | 状态 | 说明 |
|---|---|---|---|
| 1 | hifi-prototypes/ 定位 | 待确认 | 当前在 docs/ 下单独目录。它是可运行前端原型（HTML/CSS/JS），跟 design-docs/（Markdown 文档）形态不同。建议留 docs/ 下不动，待最终确认。 |
| 2 | 性能/可观测性维度空白 | 未做 | constitution 原则 IV 已机械强制，但性能/可观测性维度几乎空白。OpenAI 把日志/指标/追踪展示给 agent 是远期方向。属 Architecture Fitness harness 维度。 |
| 3 | Steering Loop 决策历史入仓库 | 考虑中 | "为什么补这条原则而不是别的"等决策过程活在对话/人脑里。铁律 2 要求入仓库。后续可补 ADR（Architecture Decision Records）。 |

## 维护规则

- 每次发现新技术债或做出架构决策，更新本文件。
- 已完成的项从"缺口"移除（不保留，保持清单新鲜）。
- 已否决的方案永久保留在"已否决"段，避免重复讨论。
- 状态分类：未做 / 考虑中 / 待确认 / 已否决。
