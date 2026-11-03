---
name: devflow-ship
description: 在工作项全部任务完成、测试与代码评审闭环后收尾时使用：对照 Definition of Done 核验完成证据、把已确认的规格与设计沉淀为长期文档资产（promotion）、写 closeout 记录。不用于实现、评审或修复。
---

# DevFlow 收尾（Ship）

## 总览

收尾回答两个问题：**这个工作项真的可以关闭吗？关闭之后给仓库留下什么？**

对应两个动作，缺一不可：

1. **完成核验**：对照 Definition of Done 逐项检查证据。"评审通过了"不等于"可以关闭"——DoD 是人在最后把关时的固定核验清单，防止靠印象关单。
2. **资产沉淀（promotion）**：把组件根下 `features/<id>/`（或团队覆盖路径）里已确认的规格与设计按原模板结构沉淀为同一组件根下的长期文档。promotion 不是重新创作交付件；原 spec / design / component-design 模板中的正文、章节、表格和锚点默认保留，只做最小清理。过程工件是给本次开发用的；长期资产是给下一个工作项、下一个人、下一轮 AI 用的。不做 promotion，工件就停在 features/ 里腐烂，组件设计基线逐渐失真。

收尾不修代码、不补测试：核验发现缺口 → 回对应阶段补，补完再回来。

## 工作流

### 1. DoD 核验

对照 `references/definition-of-done.md` 逐项检查，每项写结论（满足 / 缺口 + 去向）。任一项不满足：

| 缺口 | 回到 |
|---|---|
| 需求条目无对应通过测试 / 验收标准未覆盖 | `devflow-tdd` |
| reviews/ 缺某个门禁的评审记录，或 findings 的 Resolution 列有空缺 | 补评审（`devflow-review`）或按 findings 返工并回写 resolution |
| plan.md 门禁状态表与 reviews/ 实际记录不一致 | 修正门禁表；状态造假按 critical 处理 |
| 实现与 design.md / 组件设计漂移 | `devflow-design`（改工件）或 `devflow-tdd`（改代码） |
| 证据缺失或过期（RED/GREEN 证据行、静态分析） | `devflow-tdd` 补真实证据，不补叙述 |

### 2. 追溯终验

通读同一组件根/工件根下 `features/<id>/traceability.md`（或团队覆盖路径）：每条需求 → 设计章节 → 测试用例 → 代码/测试文件 → 证据的链路闭合；`N/A` 项有理由。断链 = spec-design-code 不一致的直接信号，按 critical 处理。

### 3. Promotion

按 `references/promotion-checklist.md` 同步长期资产：

| 过程工件 | 长期资产 | 条件 |
|---|---|---|
| `spec.md` | 组件根下 `docs/ar-specs/<id>-<slug>.md`（或团队覆盖路径） | AR/CHANGE 工作项必做；纯缺陷（无规格变更）N/A |
| `design.md` | 组件根下 `docs/ar-designs/<id>-<slug>.md`（或团队覆盖路径） | 有正式设计的工作项必做 |
| `component-design-draft.md` | 组件根下 `docs/component-design.md`（或团队覆盖路径） | 本工作项修订了组件设计时必做（模块架构师确认后） |

promotion 是**保留模板的最小清理**，不是重新改写：原 spec / design / component-design 模板中的已确认内容都应作为交付件保留，只去掉 Open Questions、过程笔记、评审应答这类不属于长期资产的过程内容；保留追溯锚点（ID、来源、测试设计用例、评审记录路径）。若长期文档已有变更记录表，追加本次修订（日期、触发工作项、摘要）；若原模板没有，不为统一格式强行重写全文。组件设计只更新受影响章节，不顺手重排其他章节。

### 4. Closeout 记录

写同一组件根/工件根下 `features/<id>/closeout.md`（或团队覆盖路径，一页内）：DoD 核验结果摘要、promotion 路径表（同步了什么 / N/A 及理由）、遗留债务清单（带去向：新工作项 / 登记的 issue）、复盘一句话（本次流程哪里最磨损，反哺 skill 或模板）。

### 5. 人确认关闭

把 closeout 呈给人。人确认后工作项关闭；组件根下 `features/<id>/`（或团队覆盖路径）原地保留（不移动、不删除），保证追溯链接长期有效。

## 风险信号

- 跳过 DoD 直接写 closeout（"评审都过了肯定没问题"）
- promotion 过度改写原模板正文、重排章节或压缩表格，导致长期交付件丢失规格 / 设计模板内容
- promotion 把过程内容原样带入长期文档（Open Questions、过程笔记、评审应答）
- 只 promote 设计不 promote 规格，或修订了组件边界却没同步 `docs/component-design.md`
- 遗留债务写"后续优化"却没有去向（没有新工作项、没有登记）
- 把"补证据"做成"补叙述"——证据必须是真实命令输出与锚点

## 自检清单

- [ ] DoD 逐项有结论；缺口已返工闭环而不是被解释掉
- [ ] traceability 链路闭合；N/A 有理由
- [ ] 应 promote 的资产已保留原模板主体并完成最小清理；必要的变更记录已追加
- [ ] closeout.md 落盘：核验摘要、promotion 表、债务去向、复盘
- [ ] 人已确认关闭；features/ 目录原地保留

## 支撑参考

| 文件 | 用途 |
|---|---|
| `references/definition-of-done.md` | Definition of Done 核验清单 |
| `references/promotion-checklist.md` | 长期资产同步对象与最小清理规则 |
